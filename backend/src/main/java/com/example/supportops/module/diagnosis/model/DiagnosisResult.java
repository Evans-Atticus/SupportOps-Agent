package com.example.supportops.module.diagnosis.model;

import java.util.List;

/** Handler 的确定性输出；本对象不包含数据库主键，便于规则复用。 */
public record DiagnosisResult(
        String summary,
        String conclusion,
        String internalSuggestion,
        String customerReply,
        double confidence,
        List<DiagnosisEvidence> evidences
) {
    public DiagnosisResult {
        evidences = List.copyOf(evidences);
    }
}
