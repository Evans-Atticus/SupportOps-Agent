package com.example.supportops.module.diagnosis.application;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.auth.manager.UserManager;
import com.example.supportops.module.auth.model.bo.UserBO;
import com.example.supportops.module.diagnosis.model.DiagnosisPlan;
import com.example.supportops.module.diagnosis.model.DiagnosisProcedure;
import com.example.supportops.module.diagnosis.model.ProcedureInstruction;
import com.example.supportops.module.diagnosis.model.dto.DiagnosisCreateDTO;
import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;
import com.example.supportops.module.diagnosis.model.vo.DiagnosisDetailVO;
import com.example.supportops.module.diagnosis.model.vo.DiagnosisTaskVO;
import com.example.supportops.module.diagnosis.model.vo.DiagnosisHistoryVO;
import com.example.supportops.module.diagnosis.persistence.DiagnosisRepository;
import com.example.supportops.module.diagnosis.persistence.PersistedDiagnosis;
import com.example.supportops.module.diagnosis.plan.ScenarioPlanRegistry;
import com.example.supportops.module.ticket.manager.TicketManager;
import com.example.supportops.module.ticket.model.bo.TicketBO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

/** 提交异步任务、执行额度/复用策略，并为短轮询组装详情。 */
@Service
public class DiagnosisApplicationService {
    private final TicketManager ticketManager;
    private final UserManager userManager;
    private final DiagnosisRepository repository;
    private final DiagnosisTaskProcessor processor;
    private final TaskExecutor executor;
    private final ScenarioPlanRegistry planRegistry;
    private final ObjectMapper objectMapper;
    private final int pollingIntervalMs;
    private final int reuseWindowMinutes;
    private final int maxDiagnosesPerMinute;
    private final int maxConcurrentDiagnoses;

    public DiagnosisApplicationService(TicketManager ticketManager, UserManager userManager,
                                       DiagnosisRepository repository, DiagnosisTaskProcessor processor,
                                       @Qualifier("diagnosisTaskExecutor") TaskExecutor executor,
                                       ScenarioPlanRegistry planRegistry, ObjectMapper objectMapper,
                                       @Value("${supportops.polling.interval-ms:800}") int pollingIntervalMs,
                                       @Value("${supportops.ai.reuse-window-minutes:5}") int reuseWindowMinutes,
                                       @Value("${supportops.ai.max-diagnoses-per-minute:5}") int maxDiagnosesPerMinute,
                                       @Value("${supportops.ai.max-concurrent-diagnoses:2}") int maxConcurrentDiagnoses) {
        this.ticketManager = ticketManager;
        this.userManager = userManager;
        this.repository = repository;
        this.processor = processor;
        this.executor = executor;
        this.planRegistry = planRegistry;
        this.objectMapper = objectMapper;
        this.pollingIntervalMs = pollingIntervalMs;
        this.reuseWindowMinutes = reuseWindowMinutes;
        this.maxDiagnosesPerMinute = maxDiagnosesPerMinute;
        this.maxConcurrentDiagnoses = maxConcurrentDiagnoses;
    }

    /** 创建任务后立即返回；模型与数据库查询都在 diagnosis 线程池执行。 */
    public DiagnosisTaskVO submit(DiagnosisCreateDTO request, String username,
                                  String idempotencyKey, String requestId) {
        TicketBO ticket = ticketManager.getRequiredByTicketNo(request.ticketNo());
        UserBO user = userManager.getRequiredByUsername(username);

        var exact = repository.findByIdempotency(user.id(), idempotencyKey);
        if (exact.isPresent()) return reused(exact.get());
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            var recent = repository.findRecentReusable(user.id(), ticket.id(), reuseWindowMinutes);
            if (recent.isPresent()) return reused(recent.get());
        }
        // 在创建任务和占用模型 Token 之前拦截突发请求；复用请求不计入限制。
        // 管理员不受应用侧的每日、每分钟和账号并发次数限制；线程池容量、单次最多两次
        // 模型调用、供应商额度熔断仍然生效，避免管理员账号绕开系统级安全边界。
        if (!isUnlimitedAdministrator(user)) {
            if (repository.countCreatedSince(user.id(), LocalDateTime.now().minusMinutes(1)) >= maxDiagnosesPerMinute
                    || repository.countActive(user.id()) >= maxConcurrentDiagnoses) {
                throw new BusinessException(ErrorCode.AI_USAGE_RATE_EXCEEDED);
            }
            if (repository.countCreatedToday(user.id()) >= user.dailyQuota()) {
                throw new BusinessException(ErrorCode.DAILY_QUOTA_EXCEEDED);
            }
        }

        long id;
        try {
            id = repository.createTask(ticket.id(), user.id(), idempotencyKey);
        } catch (DuplicateKeyException exception) {
            // 两个相同幂等请求并发到达时，数据库唯一索引决定胜者，失败方复用胜者任务。
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                var winner = repository.findByIdempotency(user.id(), idempotencyKey);
                if (winner.isPresent()) return reused(winner.get());
            }
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
        DiagnosisExecutionCommand command = new DiagnosisExecutionCommand(id,
                requestId == null ? UUID.randomUUID().toString() : requestId, ticket, request);
        try {
            executor.execute(() -> processor.process(command));
        } catch (RuntimeException exception) {
            repository.fail(id, ErrorCode.INTERNAL_ERROR, "诊断任务队列已满");
            throw exception;
        }
        return new DiagnosisTaskVO(id, DiagnosisStatus.PENDING, pollingIntervalMs, false);
    }

    @Transactional(readOnly = true)
    public DiagnosisDetailVO get(long diagnosisId) {
        PersistedDiagnosis stored = repository.getRequired(diagnosisId);
        DiagnosisPlan plan = stored.scenarioType() == null ? null : planRegistry.required(stored.scenarioType());
        DiagnosisProcedure procedure = stored.sopContent() == null ? null : new DiagnosisProcedure(
                stored.sopTitle(), stored.sopAudience(), parseInstructions(stored.sopContent()));
        return new DiagnosisDetailVO(stored.id(), stored.status(), stored.scenarioType(),
                plan == null ? null : plan.scenarioName(), stored.title(), stored.summary(), stored.confidence(),
                stored.steps(), procedure, stored.evidences(), stored.conclusion(), stored.internalSuggestion(),
                stored.customerReply(), stored.degraded(), stored.errorCode(), userFacingErrorMessage(stored));
    }

    private String userFacingErrorMessage(PersistedDiagnosis stored) {
        if (stored.errorMessage() != null && !stored.errorMessage().isBlank()) return stored.errorMessage();
        if (stored.errorCode() != null && stored.errorCode().contains(ErrorCode.AI_QUOTA_EXHAUSTED.name())) {
            return "模型额度已用完，已停止调用大模型；本次结果由规则引擎和安全模板生成。";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public DiagnosisDetailVO get(long diagnosisId, String username) {
        assertOwnership(diagnosisId, username);
        return get(diagnosisId);
    }

    @Transactional(readOnly = true)
    public List<DiagnosisHistoryVO> list(String username, int limit) {
        UserBO user = userManager.getRequiredByUsername(username);
        return repository.listRecent(user.id(), Math.max(1, Math.min(limit, 100)));
    }

    @Transactional
    public DiagnosisDetailVO apply(long diagnosisId, String username) {
        assertOwnership(diagnosisId, username);
        repository.apply(diagnosisId);
        return get(diagnosisId);
    }

    @Transactional
    public DiagnosisDetailVO discard(long diagnosisId, String username) {
        assertOwnership(diagnosisId, username);
        repository.discard(diagnosisId);
        return get(diagnosisId);
    }

    @Transactional
    public List<Map<String, Object>> addAttachments(long diagnosisId, String username,
                                                    List<MultipartFile> files) {
        assertOwnership(diagnosisId, username);
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
        if (safeFiles.isEmpty() || safeFiles.size() > 5) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择 1 至 5 个附件");
        }
        return safeFiles.stream().map(file -> {
            if (file.getSize() > 5L * 1024 * 1024) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "单个附件不能超过 5MB");
            }
            String originalName = safeFileName(file.getOriginalFilename());
            try {
                long id = repository.insertAttachment(diagnosisId, originalName,
                        file.getContentType(), file.getSize(), file.getBytes());
                return Map.<String, Object>of(
                        "id", id,
                        "fileName", originalName,
                        "contentType", file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                        "sizeBytes", file.getSize()
                );
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "附件读取失败");
            }
        }).toList();
    }

    private void assertOwnership(long diagnosisId, String username) {
        UserBO user = userManager.getRequiredByUsername(username);
        if (!repository.belongsTo(diagnosisId, user.id())) {
            // 对非所有者同样返回不存在，避免泄露其他账号的任务编号。
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
    }

    private DiagnosisTaskVO reused(long id) {
        PersistedDiagnosis stored = repository.getRequired(id);
        return new DiagnosisTaskVO(id, stored.status(), pollingIntervalMs, true);
    }

    private boolean isUnlimitedAdministrator(UserBO user) {
        return "ADMIN".equalsIgnoreCase(user.roleCode());
    }

    private String safeFileName(String name) {
        String safe = name == null ? "attachment" : name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).trim();
        if (safe.isBlank()) safe = "attachment";
        return safe.length() > 255 ? safe.substring(safe.length() - 255) : safe;
    }

    private List<ProcedureInstruction> parseInstructions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "SOP 内容不是合法 JSON");
        }
    }
}
