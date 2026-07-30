package com.example.supportops.module.diagnosis.model.vo;

import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisProcedure;
import com.example.supportops.module.diagnosis.model.DiagnosisStep;
import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;

import java.util.List;

/** 与 WorkspaceView 约定的完整报告视图。 */
public record DiagnosisDetailVO(
        Long diagnosisId,
        DiagnosisStatus status,
        ScenarioType scenarioType,
        String scenarioName,
        String title,
        String summary,
        Double confidence,
        List<DiagnosisStep> steps,
        DiagnosisProcedure procedure,
        List<DiagnosisEvidence> evidences,
        String conclusion,
        String internalSuggestion,
        String customerReply,
        boolean degraded,
        String errorCode,
        String errorMessage
) {
}
