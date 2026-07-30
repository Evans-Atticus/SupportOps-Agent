package com.example.supportops.module.diagnosis.application;

import com.example.supportops.module.diagnosis.model.dto.DiagnosisCreateDTO;
import com.example.supportops.module.ticket.model.bo.TicketBO;

/** 提交线程传给后台线程的不可变命令，避免依赖已经结束的 HttpServletRequest。 */
public record DiagnosisExecutionCommand(
        long diagnosisId,
        String requestId,
        TicketBO ticket,
        DiagnosisCreateDTO request
) {
}
