package com.example.supportops.module.system.service;

import com.example.supportops.config.PlatformIntegrationProperties;
import com.example.supportops.config.PlatformIntegrationProperties.Endpoint;
import com.example.supportops.module.system.model.vo.IntegrationStatusVO;
import com.example.supportops.module.system.model.vo.IntegrationStatusVO.ConnectorStatusVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class IntegrationStatusService {
    private final PlatformIntegrationProperties properties;

    public IntegrationStatusService(PlatformIntegrationProperties properties) {
        this.properties = properties;
    }

    public IntegrationStatusVO status() {
        return new IntegrationStatusVO(List.of(
                status("ERP", properties.getErp()),
                status("LOGISTICS", properties.getLogistics()),
                status("MEMBERSHIP", properties.getMembership()),
                status("MONITORING", properties.getMonitoring()),
                status("TICKETING", properties.getTicketing())
        ));
    }

    private ConnectorStatusVO status(String platform, Endpoint endpoint) {
        return new ConnectorStatusVO(platform, endpoint.isEnabled(), StringUtils.hasText(endpoint.getBaseUrl()));
    }
}
