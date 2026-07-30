package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

@Component
public class ProductInformationQueryHandler extends ProductKnowledgeHandlerSupport {
    @Override public ScenarioType supports() { return ScenarioType.PRODUCT_INFORMATION_QUERY; }
    @Override public DiagnosisResult diagnose(DiagnosisContext context) {
        return diagnoseKnowledge(context, "产品规格、功能或兼容性");
    }
}
