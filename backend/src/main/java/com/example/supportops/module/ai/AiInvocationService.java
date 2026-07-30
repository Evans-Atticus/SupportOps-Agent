package com.example.supportops.module.ai;

import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.ai.audit.AiCallScope;
import com.example.supportops.module.ai.audit.AiErrorMapper;
import com.example.supportops.module.ai.audit.ModelCallLogRepository;
import com.example.supportops.module.ai.reply.CustomerReplyAiService;
import com.example.supportops.module.ai.reply.ReplyDraft;
import com.example.supportops.module.ai.understanding.TicketIntent;
import com.example.supportops.module.ai.understanding.TicketUnderstandingAiService;
import com.example.supportops.module.diagnosis.persistence.DiagnosisRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** 统一模型调用入口：预占调用次数、校验结构化输出并写审计日志。 */
@Service
public class AiInvocationService {
    private static final Logger log = LoggerFactory.getLogger(AiInvocationService.class);
    private final TicketUnderstandingAiService understandingService;
    private final CustomerReplyAiService replyService;
    private final DiagnosisRepository diagnosisRepository;
    private final ModelCallLogRepository logRepository;
    private final Validator validator;
    private final boolean mockMode;
    private final int maxCalls;
    private final String modelName;
    /** 一旦供应商确认额度耗尽，本实例后续诊断直接走模板，避免重复产生失败请求。 */
    private final AtomicBoolean quotaExhausted = new AtomicBoolean(false);

    public AiInvocationService(TicketUnderstandingAiService understandingService,
                               CustomerReplyAiService replyService,
                               DiagnosisRepository diagnosisRepository,
                               ModelCallLogRepository logRepository,
                               Validator validator,
                               @Value("${supportops.ai.mode:mock}") String mode,
                               @Value("${supportops.ai.max-model-calls-per-diagnosis:3}") int maxCalls,
                               @Value("${AI_MODEL:mock-deterministic}") String modelName) {
        this.understandingService = understandingService;
        this.replyService = replyService;
        this.diagnosisRepository = diagnosisRepository;
        this.logRepository = logRepository;
        this.validator = validator;
        this.mockMode = "mock".equalsIgnoreCase(mode);
        this.maxCalls = maxCalls;
        this.modelName = modelName;
    }

    public TicketIntent understand(long diagnosisId, String requestId, String description) {
        return invoke(diagnosisId, requestId, "UNDERSTANDING", () -> understandingService.understand(description));
    }

    public ReplyDraft generateReply(long diagnosisId, String requestId, String context) {
        try {
            return invoke(diagnosisId, requestId, "CUSTOMER_REPLY", () -> replyService.generate(context));
        } catch (AiCallException exception) {
            if (exception.errorCode() != ErrorCode.AI_RESPONSE_PARSE_FAILED) throw exception;
            String retryContext = context + """

                    【结构化输出重试要求】
                    上一次输出未通过结构校验。本次必须返回且只返回一个对象：
                    - content：非空中文字符串，完整覆盖全部需求，最长 700 个汉字；
                    - tone：固定为 professional。
                    不要输出 Markdown 代码块、解释、前后缀或额外字段。
                    """;
            return invoke(diagnosisId, requestId, "CUSTOMER_REPLY_RETRY",
                    () -> replyService.generate(retryContext));
        }
    }

    private <T> T invoke(long diagnosisId, String requestId, String callType, Supplier<T> invocation) {
        if (quotaExhausted.get()) {
            throw new AiCallException(ErrorCode.AI_QUOTA_EXHAUSTED,
                    new IllegalStateException("模型额度已用完，已停止调用大模型"));
        }
        if (!diagnosisRepository.reserveModelCall(diagnosisId, maxCalls)) {
            throw new AiCallException(ErrorCode.AI_UNAVAILABLE,
                    new IllegalStateException("单次诊断模型调用次数不能超过 " + maxCalls));
        }
        long logId = logRepository.start(diagnosisId, requestId, callType,
                mockMode ? "MOCK" : "OPENAI_COMPATIBLE", modelName);
        long started = System.nanoTime();
        try (AiCallScope ignored = AiCallScope.open(logId, diagnosisId, callType, started)) {
            T value = invocation.get();
            validate(value);
            // Mock 不经过 ChatModel Listener，因此在统一入口补齐成功审计。
            if (mockMode) logRepository.success(logId, modelName, null, null, elapsed(started));
            return value;
        } catch (AiCallException exception) {
            log.warn("AI structured output validation failed: diagnosisId={}, callType={}, errorCode={}, reason={}",
                    diagnosisId, callType, exception.errorCode(), safeReason(exception.getCause()));
            logRepository.failure(logId, exception.errorCode().name(), elapsed(started));
            throw exception;
        } catch (Throwable error) {
            ErrorCode code = AiErrorMapper.code(error);
            log.warn("AI invocation failed: diagnosisId={}, callType={}, errorCode={}, exceptionType={}",
                    diagnosisId, callType, code, rootType(error));
            if (code == ErrorCode.AI_QUOTA_EXHAUSTED) quotaExhausted.set(true);
            logRepository.failure(logId, code.name(), elapsed(started));
            throw new AiCallException(code, error);
        }
    }

    private <T> void validate(T value) {
        if (value == null) throw new AiCallException(ErrorCode.AI_RESPONSE_PARSE_FAILED,
                new IllegalArgumentException("模型返回空对象"));
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) throw new AiCallException(ErrorCode.AI_RESPONSE_PARSE_FAILED,
                new IllegalArgumentException("模型结构化输出校验失败: " + violations.iterator().next().getMessage()));
    }

    private long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private String safeReason(Throwable error) {
        if (error instanceof IllegalArgumentException && error.getMessage() != null
                && error.getMessage().startsWith("模型")) {
            return error.getMessage();
        }
        return rootType(error);
    }

    private String rootType(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null ? "unknown" : current.getClass().getName();
    }
}
