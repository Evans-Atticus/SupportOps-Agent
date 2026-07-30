package com.example.supportops.module.diagnosis.model;

/**
 * 一条可追溯证据。sourceRecordId 指向原业务记录，避免结论只剩自然语言、无法复核。
 */
public record DiagnosisEvidence(
        String source,
        Long sourceRecordId,
        String field,
        String label,
        String value,
        String description,
        double confidence
) {
    public DiagnosisEvidence {
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("证据置信度必须在 0 到 1 之间");
        }
    }
}
