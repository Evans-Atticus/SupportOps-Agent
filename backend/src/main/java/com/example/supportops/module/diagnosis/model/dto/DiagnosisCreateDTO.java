package com.example.supportops.module.diagnosis.model.dto;

import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** scenarioType 可省略；无 AI 的第二周版本会优先使用工单中已审核的 scenarioHint。 */
public record DiagnosisCreateDTO(
        /** 必填，对外稳定的工单编号，而不是数据库自增主键。 */
        @NotBlank(message = "工单号不能为空") String ticketNo,
        /** 可选；显式值优先于工单内保存的业务编号。 */
        String businessNo,
        /** 可选；显式描述优先用于 AI 理解，最大 2000 字符。 */
        @Size(max = 2000, message = "问题描述不能超过 2000 个字符") String description,
        /** 可选；未传时使用工单 scenarioHint，UNKNOWN 会被拒绝。 */
        ScenarioType scenarioType
) {
}
