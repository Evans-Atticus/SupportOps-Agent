package com.example.supportops.module.ai.understanding;

import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 大模型对工单的结构化理解结果。它只描述意图和抽取实体，不允许携带最终根因。
 */
public record TicketIntent(
        @NotNull ScenarioType scenarioType,
        List<ScenarioType> scenarioTypes,
        String businessNo,
        @NotBlank String summary,
        @NotBlank String emotion,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        List<String> missingInformation
) {
    public TicketIntent {
        scenarioTypes = normalizeScenarios(scenarioType, scenarioTypes);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }

    /** 兼容旧调用方；单问题会自动形成只包含主场景的列表。 */
    public TicketIntent(ScenarioType scenarioType, String businessNo, String summary, String emotion,
                        double confidence, List<String> missingInformation) {
        this(scenarioType, List.of(scenarioType), businessNo, summary, emotion, confidence, missingInformation);
    }

    private static List<ScenarioType> normalizeScenarios(ScenarioType primary, List<ScenarioType> values) {
        java.util.LinkedHashSet<ScenarioType> normalized = new java.util.LinkedHashSet<>();
        if (values != null) values.stream()
                .filter(java.util.Objects::nonNull)
                .filter(value -> value != ScenarioType.UNKNOWN)
                .forEach(normalized::add);
        if (primary != null && primary != ScenarioType.UNKNOWN) {
            normalized.remove(primary);
            java.util.LinkedHashSet<ScenarioType> ordered = new java.util.LinkedHashSet<>();
            ordered.add(primary);
            ordered.addAll(normalized);
            normalized = ordered;
        }
        return List.copyOf(normalized);
    }
}
