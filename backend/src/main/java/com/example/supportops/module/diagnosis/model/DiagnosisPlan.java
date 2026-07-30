package com.example.supportops.module.diagnosis.model;

import com.example.supportops.module.diagnosis.model.enums.ScenarioType;

import java.util.List;

/** 白名单计划：明确一个场景允许读取哪些数据，而不是接受模型返回任意类名或工具名。 */
public record DiagnosisPlan(
        ScenarioType scenarioType,
        String scenarioName,
        String title,
        List<String> querySteps
) {
    public DiagnosisPlan {
        querySteps = List.copyOf(querySteps);
    }
}
