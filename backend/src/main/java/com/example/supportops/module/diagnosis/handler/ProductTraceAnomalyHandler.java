package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.trace.model.TraceModels;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 产品溯源异常：只根据后端重新读取的生命周期事件判断风险，不信任前端展示字段。 */
@Component
public class ProductTraceAnomalyHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() {
        return ScenarioType.PRODUCT_TRACE_ANOMALY;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        TraceModels.TraceDetail detail = context.traceDetail();
        if (detail == null || detail.events().isEmpty()) {
            throw new IllegalStateException("缺少产品溯源事件快照");
        }
        List<TraceModels.TraceEvent> risks = detail.events().stream().filter(this::isRisk).toList();
        boolean anomalous = !risks.isEmpty();
        List<DiagnosisEvidence> evidence = (anomalous ? risks : detail.events()).stream().map(event ->
                HandlerSupport.evidence("trace_events", Math.abs((long) event.eventId().hashCode()), "status",
                        event.stage() + "状态", event.status(), event.source() + " / " + event.sourceRecordNo()
                                + " / " + event.occurredAt())).toList();
        return new DiagnosisResult("产品全生命周期事件核验完成",
                anomalous ? "产品批次存在质量或履约风险，影响范围需要冻结并复核" : "产品全生命周期状态一致，未发现风险节点",
                anomalous ? "保持风险库存冻结，复核质检报告与销售流向后提交召回审批" : "无需发起召回，保留当前溯源快照",
                anomalous ? "您好，经核查该产品批次存在质量或履约异常，相关库存和发货已进入复核流程，我们会同步后续处理结果。"
                        : "您好，经核查该产品从生产、质检到交付的记录完整，当前未发现异常。",
                anomalous ? 0.98 : 0.94, evidence);
    }

    private boolean isRisk(TraceModels.TraceEvent event) {
        String status = event.status() == null ? "" : event.status().toLowerCase(Locale.ROOT);
        return status.contains("不合格") || status.contains("冻结") || status.contains("超时")
                || status.contains("暂停") || status.contains("召回") || status.contains("异常");
    }
}
