package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;

/** 包内辅助方法让 Handler 保持聚焦业务条件，不重复拼装证据字段。 */
final class HandlerSupport {
    // 工具类不需要实例化，私有构造器阻止外部误用。
    private HandlerSupport() {
    }

    static DiagnosisEvidence evidence(String source, Long id, String field, String label,
                                      Object value, String description) {
        // 规则证据来自数据库确定事实，因此默认置信度为 1.0。
        return new DiagnosisEvidence(source, id, field, label, String.valueOf(value), description, 1.0);
    }
}
