package com.example.supportops.module.ticket.integration;

import com.example.supportops.module.ticket.model.enums.TicketPriority;

/**
 * 工单平台的内部标准契约。厂商字段名不同时，只需在 HTTP 适配器中转换到此结构。
 */
public record ExternalTicketRecord(
        Long customerId,
        String ticketNo,
        String businessNo,
        String channel,
        String description,
        String scenarioHint,
        TicketPriority priority
) {
}
