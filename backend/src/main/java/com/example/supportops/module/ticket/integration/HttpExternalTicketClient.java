package com.example.supportops.module.ticket.integration;

import com.example.supportops.config.PlatformIntegrationProperties;
import com.example.supportops.infrastructure.integration.PlatformHttpClient;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 默认 HTTP 工单适配器，预期 GET /tickets/{ticketNo} 返回标准工单契约。 */
@Component
public class HttpExternalTicketClient implements ExternalTicketClient {
    private final PlatformHttpClient httpClient;
    private final PlatformIntegrationProperties properties;

    public HttpExternalTicketClient(PlatformHttpClient httpClient, PlatformIntegrationProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public ExternalTicketRecord getByTicketNo(String ticketNo) {
        String safeTicketNo = UriUtils.encodePathSegment(ticketNo, StandardCharsets.UTF_8);
        return httpClient.getObject("工单平台", properties.getTicketing(),
                "/tickets/" + safeTicketNo, Map.of(), ExternalTicketRecord.class);
    }
}
