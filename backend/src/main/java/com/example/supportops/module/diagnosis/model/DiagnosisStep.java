package com.example.supportops.module.diagnosis.model;

/** 诊断步骤既用于审计，也直接驱动前端进度展示。 */
public record DiagnosisStep(String code, String title, String status, long durationMs, String detail) {
}
