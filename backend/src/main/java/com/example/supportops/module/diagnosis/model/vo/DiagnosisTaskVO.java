package com.example.supportops.module.diagnosis.model.vo;

import com.example.supportops.module.diagnosis.model.enums.DiagnosisStatus;

/** POST 接口的轻量响应；前端收到任务 ID 后按 pollAfterMs 查询详情。 */
public record DiagnosisTaskVO(Long diagnosisId, DiagnosisStatus status, int pollAfterMs, boolean reused) {
}
