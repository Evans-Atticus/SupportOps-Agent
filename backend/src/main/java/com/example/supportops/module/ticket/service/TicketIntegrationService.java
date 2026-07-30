package com.example.supportops.module.ticket.service;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.ticket.convert.TicketConvert;
import com.example.supportops.module.ticket.integration.ExternalTicketClient;
import com.example.supportops.module.ticket.integration.ExternalTicketRecord;
import com.example.supportops.module.ticket.manager.TicketManager;
import com.example.supportops.module.ticket.model.bo.TicketBO;
import com.example.supportops.module.ticket.model.enums.TicketPriority;
import com.example.supportops.module.ticket.model.enums.TicketStatus;
import com.example.supportops.module.ticket.model.vo.TicketImportVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 将外部工单幂等落入本地，使诊断、审计和历史报告仍有稳定外键。 */
@Service
public class TicketIntegrationService {
    private final ExternalTicketClient externalTicketClient;
    private final TicketManager ticketManager;

    public TicketIntegrationService(ExternalTicketClient externalTicketClient, TicketManager ticketManager) {
        this.externalTicketClient = externalTicketClient;
        this.ticketManager = ticketManager;
    }

    @Transactional
    public TicketImportVO importTicket(String ticketNo) {
        var existing = ticketManager.findByTicketNo(ticketNo);
        if (existing.isPresent()) return new TicketImportVO(TicketConvert.toVO(existing.get()), false, "LOCAL_CACHE");

        ExternalTicketRecord external = externalTicketClient.getByTicketNo(ticketNo);
        validate(external);
        LocalDateTime now = LocalDateTime.now();
        TicketBO created = ticketManager.create(new TicketBO(null, external.ticketNo(), external.customerId(),
                external.businessNo(), external.channel(), external.description(), external.scenarioHint(),
                TicketStatus.OPEN, external.priority() == null ? TicketPriority.NORMAL : external.priority(), now, now));
        return new TicketImportVO(TicketConvert.toVO(created), true, "EXTERNAL_TICKETING");
    }

    private void validate(ExternalTicketRecord ticket) {
        if (ticket == null || ticket.customerId() == null || !StringUtils.hasText(ticket.ticketNo())
                || !StringUtils.hasText(ticket.businessNo()) || !StringUtils.hasText(ticket.channel())
                || !StringUtils.hasText(ticket.description())) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "外部工单缺少必填字段");
        }
    }
}
