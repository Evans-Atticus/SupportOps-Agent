package com.example.supportops.module.ai.reply;

import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.ProcedureInstruction;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** 只向回复模型发送对客所需事实，主动移除表名、记录主键和内部错误字段。 */
@Component
public class VerifiedReplyContextBuilder {
    private final ObjectMapper objectMapper;

    public VerifiedReplyContextBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(DiagnosisContext context, DiagnosisResult result) {
        return build(List.of(context), result, "", List.of(context.scenarioType()));
    }

    /** 给回复模型提供客户真实诉求和全部已验证结果，但不暴露底层表名、主键或任意查询能力。 */
    public String build(List<DiagnosisContext> contexts, DiagnosisResult result, String customerQuestion,
                        List<ScenarioType> scenarios) {
        StringBuilder value = new StringBuilder()
                .append("客户原始问题：").append(customerQuestion == null ? "" : customerQuestion).append('\n')
                .append("已识别需求：\n");
        for (int i = 0; i < scenarios.size(); i++) {
            value.append(i + 1).append(". ").append(scenarioLabel(scenarios.get(i))).append('\n');
        }
        value.append("规则结论：").append(result.conclusion()).append('\n')
                .append("对客可见下一步：").append(safeSuggestion(result.internalSuggestion())).append('\n')
                .append("后端事实答复草稿：\n").append(result.customerReply()).append('\n')
                .append("已验证证据：\n");
        for (DiagnosisEvidence evidence : result.evidences()) {
            value.append("- ").append(evidence.label()).append(" = ").append(evidence.value()).append('\n');
        }
        value.append("SOP：\n");
        Set<String> instructions = new LinkedHashSet<>();
        for (DiagnosisContext context : contexts) {
            for (ProcedureInstruction instruction : parse(context.sop().content())) {
                instructions.add(instruction.text());
            }
        }
        instructions.forEach(instruction -> value.append("- ").append(instruction).append('\n'));
        value.append("产品知识召回约束：\n");
        boolean hasProductKnowledge = contexts.stream().anyMatch(context -> !context.productKnowledge().isEmpty());
        if (hasProductKnowledge) {
            value.append("- 仅可使用上方‘已验证证据’中的产品资料片段回答产品事实。\n")
                    .append("- 必须结合客户原问题归纳命中片段，不能照抄无关段落或用常识补齐。\n")
                    .append("- 多个片段冲突时指出版本或来源冲突，不自行选择一个当作事实。\n");
        } else if (scenarios.stream().anyMatch(this::isProductKnowledgeScenario)) {
            value.append("- 当前 SKU 没有命中可用产品资料，必须明确说明缺少依据并请求补充，不得编造。\n");
        }
        return value.toString();
    }

    private String scenarioLabel(ScenarioType scenario) {
        return switch (scenario) {
            case ORDER_INFORMATION_QUERY -> "查询订单商品、金额或状态";
            case PRODUCT_INFORMATION_QUERY -> "查询产品规格、功能或兼容性";
            case PRODUCT_USAGE_GUIDANCE -> "查询产品安装、使用或保养方法";
            case PRODUCT_TROUBLESHOOTING -> "查询产品故障排查或售后指引";
            case PAYMENT_SUCCESS_ORDER_PENDING -> "核验支付成功但订单未更新";
            case ORDER_CANCELLED_BUT_CHARGED -> "核验订单取消、扣款与退款状态";
            case COUPON_UNAVAILABLE -> "核验优惠券使用条件";
            case MEMBER_BENEFIT_NOT_RECEIVED -> "核验会员权益发放状态";
            case LOGISTICS_TRACKING_QUERY -> "查询物流路线、当前位置与预计送达";
            case LOGISTICS_STATUS_NOT_SYNCED -> "核验平台与承运商物流状态差异";
            case API_FREQUENT_FAILURE -> "核验接口调用异常";
            case INVOICE_ISSUE_FAILED -> "核验订单开票问题";
            case PRODUCT_TRACE_ANOMALY -> "核验产品全链路溯源异常";
            case UNKNOWN -> "待澄清的问题";
        };
    }

    private boolean isProductKnowledgeScenario(ScenarioType scenario) {
        return scenario == ScenarioType.PRODUCT_INFORMATION_QUERY
                || scenario == ScenarioType.PRODUCT_USAGE_GUIDANCE
                || scenario == ScenarioType.PRODUCT_TROUBLESHOOTING;
    }

    private List<ProcedureInstruction> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String safeSuggestion(String suggestion) {
        if (suggestion == null) return "按既定流程继续核查";
        // “提交/触发/补发”在内部建议中只是待执行动作，对客时改写为不承诺完成的表达。
        return suggestion.replace("提交", "核查是否需要提交")
                .replace("触发", "核查是否需要触发")
                .replace("补发", "核查是否需要补发");
    }
}
