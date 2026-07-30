package com.example.supportops.module.ticket.integration;

/** 外部工单平台端口；后续可新增 Zendesk、企业微信或自建客服系统适配器。 */
public interface ExternalTicketClient {
    ExternalTicketRecord getByTicketNo(String ticketNo);
}
