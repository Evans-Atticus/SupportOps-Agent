package com.example.supportops.module.diagnosis.model.enums;

/**
 * 诊断任务状态机。第二周同步规则链直接完成到 SUCCESS，其他中间状态供第三周异步流程使用。
 */
public enum DiagnosisStatus {
    PENDING,
    UNDERSTANDING,
    QUERYING,
    DIAGNOSING,
    GENERATING_REPLY,
    SUCCESS,
    FAILED,
    DEGRADED_SUCCESS,
    DISCARDED
}
