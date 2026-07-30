package com.example.supportops.module.diagnosis.persistence;

import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisStep;
import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;

import java.util.List;

/** 数据访问层的聚合读取结果，不直接作为 API 返回，防止数据库结构泄漏到接口层。 */
public record PersistedDiagnosis(
        Long id, DiagnosisStatus status, ScenarioType scenarioType, Double confidence, boolean degraded,
        String errorCode, String errorMessage,
        String title, String summary, String conclusion, String internalSuggestion, String customerReply,
        String sopTitle, String sopAudience, String sopContent,
        List<DiagnosisStep> steps, List<DiagnosisEvidence> evidences
) {
}
