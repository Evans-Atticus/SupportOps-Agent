package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

@Component
public class ProductTroubleshootingHandler extends ProductKnowledgeHandlerSupport {
    @Override public ScenarioType supports() { return ScenarioType.PRODUCT_TROUBLESHOOTING; }
    @Override public DiagnosisResult diagnose(DiagnosisContext context) {
        return diagnoseKnowledge(context, "故障现象、排查步骤或售后处理");
    }
}
