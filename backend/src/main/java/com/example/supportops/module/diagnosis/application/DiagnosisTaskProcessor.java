package com.example.supportops.module.diagnosis.application;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.ai.AiCallException;
import com.example.supportops.module.ai.AiInvocationService;
import com.example.supportops.module.ai.fallback.KeywordFallbackClassifier;
import com.example.supportops.module.ai.fallback.TemplateReplyService;
import com.example.supportops.module.ai.reply.ReplyDraft;
import com.example.supportops.module.ai.reply.VerifiedReplyContextBuilder;
import com.example.supportops.module.ai.understanding.TicketIntent;
import com.example.supportops.module.diagnosis.handler.DiagnosisHandlerRegistry;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.diagnosis.persistence.DiagnosisRepository;
import com.example.supportops.module.diagnosis.plan.ScenarioPlanRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 后台执行理解、查询、规则诊断、回复和持久化五个阶段。 */
@Service
public class DiagnosisTaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(DiagnosisTaskProcessor.class);

    private final DiagnosisRepository repository;
    private final AiInvocationService aiInvocationService;
    private final KeywordFallbackClassifier fallbackClassifier;
    private final DiagnosisContextFactory contextFactory;
    private final DiagnosisHandlerRegistry handlerRegistry;
    private final ScenarioPlanRegistry planRegistry;
    private final VerifiedReplyContextBuilder replyContextBuilder;
    private final TemplateReplyService templateReplyService;
    private final double minimumConfidence;

    public DiagnosisTaskProcessor(DiagnosisRepository repository,
                                  AiInvocationService aiInvocationService,
                                  KeywordFallbackClassifier fallbackClassifier,
                                  DiagnosisContextFactory contextFactory,
                                  DiagnosisHandlerRegistry handlerRegistry,
                                  ScenarioPlanRegistry planRegistry,
                                  VerifiedReplyContextBuilder replyContextBuilder,
                                  TemplateReplyService templateReplyService,
                                  @Value("${supportops.ai.minimum-intent-confidence:0.55}") double minimumConfidence) {
        this.repository = repository;
        this.aiInvocationService = aiInvocationService;
        this.fallbackClassifier = fallbackClassifier;
        this.contextFactory = contextFactory;
        this.handlerRegistry = handlerRegistry;
        this.planRegistry = planRegistry;
        this.replyContextBuilder = replyContextBuilder;
        this.templateReplyService = templateReplyService;
        this.minimumConfidence = minimumConfidence;
    }

    public void process(DiagnosisExecutionCommand command) {
        long id = command.diagnosisId();
        boolean degraded = false;
        String degradationCode = null;
        try {
            repository.startTask(id);

            PhaseResult<TicketIntent> understanding = understand(command);
            TicketIntent intent = understanding.value();
            degraded = understanding.degraded();
            degradationCode = understanding.errorCode();

            List<ScenarioType> scenarios = resolveScenarios(command, intent);
            if (scenarios.isEmpty() || scenarios.contains(ScenarioType.UNKNOWN)) {
                throw new BusinessException(ErrorCode.UNKNOWN_SCENARIO,
                        "无法识别诊断场景，请补充业务现象和业务编号");
            }
            scenarios.forEach(planRegistry::required);
            ScenarioType primaryScenario = scenarios.get(0);
            String businessNo = firstText(command.request().businessNo(), intent.businessNo(), command.ticket().businessNo());

            repository.updateStatus(id, DiagnosisStatus.QUERYING, primaryScenario);
            repository.startStep(id, "QUERY_BUSINESS");
            long queryStarted = System.nanoTime();
            List<DiagnosisContext> contexts = scenarios.stream()
                    .map(scenario -> contextFactory.create(scenario, command.ticket().ticketNo(), businessNo,
                            description(command)))
                    .toList();
            repository.finishStep(id, "QUERY_BUSINESS", elapsed(queryStarted),
                    scenarios.size() == 1 ? "业务快照查询完成" : "已查询 " + scenarios.size() + " 个问题所需的业务快照");

            repository.updateStatus(id, DiagnosisStatus.DIAGNOSING, primaryScenario);
            repository.startStep(id, "DIAGNOSE_RULES");
            long ruleStarted = System.nanoTime();
            List<DiagnosisResult> results = contexts.stream()
                    .map(context -> handlerRegistry.required(context.scenarioType()).diagnose(context))
                    .toList();
            DiagnosisResult result = combineResults(scenarios, results);
            repository.finishStep(id, "DIAGNOSE_RULES", elapsed(ruleStarted),
                    scenarios.stream().map(scenario -> handlerRegistry.required(scenario).getClass().getSimpleName())
                            .reduce((left, right) -> left + " + " + right).orElse("规则执行完成"));

            repository.updateStatus(id, DiagnosisStatus.GENERATING_REPLY, primaryScenario);
            repository.startStep(id, "GENERATE_REPLY");
            long replyStarted = System.nanoTime();
            String reply;
            DiagnosisContext primaryContext = contexts.get(0);
            if (scenarios.stream().anyMatch(this::requiresDeterministicReply)) {
                // 发票资格、产品使用步骤和售后安全指引属于确定性业务规则，直接使用已验证资料，
                // 即使与订单/物流等问题同时出现，也不能让模型扩写无关内容、丢失安全动作或
                // 虚构“问题未完整显示”；联合查询直接使用规则层已经逐项组合的完整回复。
                reply = result.customerReply();
                repository.finishStep(id, "GENERATE_REPLY", elapsed(replyStarted),
                        "已使用确定性业务回复");
            } else try {
                // 回复模型看到客户原问题、全部需求和脱敏证据，用于组织针对性的完整回答；它不拥有数据库或任意工具权限。
                String safeContext = replyContextBuilder.build(contexts, result, description(command), scenarios);
                ReplyDraft draft = aiInvocationService.generateReply(id, command.requestId(), safeContext);
                reply = draft.content();
                repository.finishStep(id, "GENERATE_REPLY", elapsed(replyStarted),
                        scenarios.size() == 1 ? "AI 已依据验证证据生成客服回复"
                                : "AI 已依据验证证据逐项回答 " + scenarios.size() + " 个客户问题");
            } catch (AiCallException exception) {
                degraded = true;
                degradationCode = mergeCode(degradationCode, exception.errorCode().name());
                // 模型不可用时仍返回规则引擎已生成的完整事实答案，且不能丢失复合问题中的任一项。
                reply = scenarios.size() > 1 || isFactReplyScenario(primaryScenario)
                        ? result.customerReply() : templateReplyService.render(primaryScenario, result);
                repository.degradeStep(id, "GENERATE_REPLY", elapsed(replyStarted),
                        "模型不可用，已使用安全模板：" + exception.errorCode().name());
            }

            repository.startStep(id, "BUILD_REPORT");
            long reportStarted = System.nanoTime();
            repository.finishStep(id, "BUILD_REPORT", elapsed(reportStarted), "报告、证据和 SOP 已组装");
            String reportTitle = scenarios.size() == 1 ? primaryContext.sop().title() : "多问题联合查询";
            repository.complete(id, primaryScenario, reportTitle, result, primaryContext.sop(), reply,
                    degraded, degradationCode);
        } catch (Throwable error) {
            ErrorCode code = error instanceof BusinessException business ? business.getErrorCode()
                    : error instanceof AiCallException ai ? ai.errorCode() : ErrorCode.INTERNAL_ERROR;
            repository.failRunningSteps(id, code.name() + ": " + error.getMessage());
            repository.fail(id, code, error.getMessage());
            log.error("Diagnosis task failed, diagnosisId={}, errorCode={}", id, code, error);
        }
    }

    private PhaseResult<TicketIntent> understand(DiagnosisExecutionCommand command) {
        long id = command.diagnosisId();
        repository.startStep(id, "UNDERSTAND_TICKET");
        long started = System.nanoTime();
        // 只有调用方明确指定场景时才跳过模型。历史工单 hint 和关键词都只是模型失败后的兜底线索，
        // 不能在理解当前客户问题之前抢先决定场景。
        ScenarioType explicitScenario = command.request().scenarioType();
        if (explicitScenario != null && explicitScenario != ScenarioType.UNKNOWN) {
            repository.finishStep(id, "UNDERSTAND_TICKET", elapsed(started),
                    "调用方明确指定场景=" + explicitScenario + "，跳过模型意图识别");
            return new PhaseResult<>(new TicketIntent(explicitScenario, command.request().businessNo(),
                    description(command), "neutral", 1.0, java.util.List.of()), false, null);
        }
        try {
            TicketIntent intent = aiInvocationService.understand(id, command.requestId(), description(command));
            if (intent.confidence() >= minimumConfidence && intent.scenarioType() != ScenarioType.UNKNOWN) {
                repository.finishStep(id, "UNDERSTAND_TICKET", elapsed(started),
                        "场景=" + intent.scenarioType() + "，置信度=" + intent.confidence());
                return new PhaseResult<>(intent, false, null);
            }
            List<ScenarioType> fallbacks = fallbackClassifier.classifyAll(description(command), command.ticket().scenarioHint());
            ScenarioType fallback = fallbacks.get(0);
            TicketIntent degradedIntent = new TicketIntent(fallback, fallbacks, intent.businessNo(), intent.summary(), intent.emotion(),
                    intent.confidence(), intent.missingInformation());
            repository.degradeStep(id, "UNDERSTAND_TICKET", elapsed(started),
                    "模型置信度不足，使用关键词/可信 hint 兜底");
            return new PhaseResult<>(degradedIntent, true, "LOW_INTENT_CONFIDENCE");
        } catch (AiCallException exception) {
            List<ScenarioType> fallbacks = fallbackClassifier.classifyAll(description(command), command.ticket().scenarioHint());
            ScenarioType fallback = fallbacks.get(0);
            TicketIntent degradedIntent = new TicketIntent(fallback, fallbacks, null, description(command), "neutral", 0.3,
                    java.util.List.of("模型不可用，已使用演示分类兜底"));
            repository.degradeStep(id, "UNDERSTAND_TICKET", elapsed(started),
                    "模型理解失败，使用关键词/可信 hint 兜底：" + exception.errorCode().name());
            return new PhaseResult<>(degradedIntent, true, exception.errorCode().name());
        }
    }

    private List<ScenarioType> resolveScenarios(DiagnosisExecutionCommand command, TicketIntent intent) {
        ScenarioType explicit = command.request().scenarioType();
        if (explicit != null && explicit != ScenarioType.UNKNOWN) return List.of(explicit);
        if (intent.scenarioTypes() != null && !intent.scenarioTypes().isEmpty()) return intent.scenarioTypes();
        return List.of(intent.scenarioType());
    }

    private DiagnosisResult combineResults(List<ScenarioType> scenarios, List<DiagnosisResult> results) {
        if (results.size() == 1) return results.get(0);
        StringBuilder conclusion = new StringBuilder();
        StringBuilder suggestion = new StringBuilder();
        StringBuilder reply = new StringBuilder("已为您同时查询以下 ").append(results.size()).append(" 项信息：");
        List<com.example.supportops.module.diagnosis.model.DiagnosisEvidence> evidences = new ArrayList<>();
        double confidence = 1.0;
        for (int i = 0; i < results.size(); i++) {
            DiagnosisResult item = results.get(i);
            String name = planRegistry.required(scenarios.get(i)).scenarioName();
            int number = i + 1;
            conclusion.append(number).append(". ").append(name).append("：").append(item.conclusion());
            suggestion.append(number).append(". ").append(name).append("：").append(item.internalSuggestion());
            reply.append("\n").append(number).append(". ").append(name).append("：").append(item.customerReply());
            if (i < results.size() - 1) {
                conclusion.append("\n");
                suggestion.append("\n");
            }
            evidences.addAll(item.evidences());
            confidence = Math.min(confidence, item.confidence());
        }
        return new DiagnosisResult("已完成 " + results.size() + " 项问题的联合查询",
                conclusion.toString(), suggestion.toString(), reply.toString(), confidence, evidences);
    }

    private boolean isFactReplyScenario(ScenarioType scenario) {
        return scenario == ScenarioType.ORDER_INFORMATION_QUERY || scenario == ScenarioType.LOGISTICS_TRACKING_QUERY
                || scenario == ScenarioType.PRODUCT_INFORMATION_QUERY
                || scenario == ScenarioType.PRODUCT_USAGE_GUIDANCE
                || scenario == ScenarioType.PRODUCT_TROUBLESHOOTING;
    }

    private boolean requiresDeterministicReply(ScenarioType scenario) {
        return scenario == ScenarioType.INVOICE_ISSUE_FAILED
                || scenario == ScenarioType.PRODUCT_USAGE_GUIDANCE
                || scenario == ScenarioType.PRODUCT_TROUBLESHOOTING;
    }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        throw new BusinessException(ErrorCode.MISSING_BUSINESS_NO);
    }

    private String description(DiagnosisExecutionCommand command) {
        String explicit = command.request().description();
        return explicit != null && !explicit.isBlank() ? explicit : command.ticket().description();
    }

    private String mergeCode(String current, String next) {
        if (current == null) return next;
        // 理解和回复阶段可能命中同一熔断原因，报告中只保留一次稳定错误码。
        return java.util.Arrays.asList(current.split(",")).contains(next) ? current : current + "," + next;
    }

    private long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private record PhaseResult<T>(T value, boolean degraded, String errorCode) {
    }
}
