package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

@Component
public class ProductUsageGuidanceHandler extends ProductKnowledgeHandlerSupport {
    @Override public ScenarioType supports() { return ScenarioType.PRODUCT_USAGE_GUIDANCE; }
    @Override public DiagnosisResult diagnose(DiagnosisContext context) {
        return diagnoseKnowledge(context, "安装、使用或保养方法");
    }
}
