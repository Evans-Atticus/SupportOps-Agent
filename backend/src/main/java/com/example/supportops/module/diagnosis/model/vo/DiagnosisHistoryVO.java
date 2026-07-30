package com.example.supportops.module.diagnosis.model.vo;

import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;

import java.time.LocalDateTime;

/** 工作台历史列表的精简视图，避免为每一行重复加载步骤、证据和 SOP。 */
public record DiagnosisHistoryVO(
        Long diagnosisId,
        String ticketNo,
        String businessNo,
        String description,
        DiagnosisStatus status,
        ScenarioType scenarioType,
        String title,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
