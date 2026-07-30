package com.example.supportops.module.system.model.vo;

import java.util.List;

/** 只返回开关和配置完整性，不返回 URL、Token 等部署细节。 */
public record IntegrationStatusVO(List<ConnectorStatusVO> connectors) {
    public record ConnectorStatusVO(String platform, boolean enabled, boolean configured) {
    }
}
