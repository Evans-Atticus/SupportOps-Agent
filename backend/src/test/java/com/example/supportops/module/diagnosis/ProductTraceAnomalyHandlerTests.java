package com.example.supportops.module.diagnosis;

import com.example.supportops.module.business.model.query.SopRecord;
import com.example.supportops.module.diagnosis.handler.ProductTraceAnomalyHandler;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.trace.model.TraceModels;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTraceAnomalyHandlerTests {
    @Test
    void identifiesQualityAndRecallRiskFromTrustedTraceEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        TraceModels.TraceDetail trace = new TraceModels.TraceDetail("TR1-RISK-B06-42", "SKU-B026",
                "LOT-20260718-B06", null, "风险冻结", List.of(
                new TraceModels.TraceEvent("EV-1", "质检", "过程检验", "QMS", "QC-0718-206",
                        "苏州二厂", now, "不合格"),
                new TraceModels.TraceEvent("EV-2", "仓储", "冻结库存", "WMS", "RC-001",
                        "华东一号仓", now.plusMinutes(2), "已冻结")));
        SopRecord sop = new SopRecord(0L, ScenarioType.PRODUCT_TRACE_ANOMALY.name(), "溯源 SOP",
                "SUPPORT", 1, "[]", true, now);
        DiagnosisContext context = new DiagnosisContext(ScenarioType.PRODUCT_TRACE_ANOMALY, "TK-QC-206",
                "LOT-20260718-B06", now, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), sop, trace);

        var result = new ProductTraceAnomalyHandler().diagnose(context);

        assertTrue(result.conclusion().contains("风险"));
        assertTrue(result.evidences().size() >= 2);
    }
}
