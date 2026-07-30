package com.example.supportops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部平台连接配置。凭证只从后端环境变量读取，不向前端或状态接口暴露。
 * 每个连接器可独立开启，便于按 ERP、物流、会员等平台逐步迁移。
 */
@ConfigurationProperties(prefix = "supportops.integrations")
public class PlatformIntegrationProperties {
    private Endpoint erp = new Endpoint();
    private Endpoint logistics = new Endpoint();
    private Endpoint membership = new Endpoint();
    private Endpoint monitoring = new Endpoint();
    private Endpoint ticketing = new Endpoint();
    private Endpoint pim = new Endpoint();
    private Endpoint srm = new Endpoint();
    private Endpoint mes = new Endpoint();
    private Endpoint qms = new Endpoint();
    private Endpoint wms = new Endpoint();
    private Endpoint tms = new Endpoint();

    public Endpoint getErp() { return erp; }
    public void setErp(Endpoint erp) { this.erp = erp; }
    public Endpoint getLogistics() { return logistics; }
    public void setLogistics(Endpoint logistics) { this.logistics = logistics; }
    public Endpoint getMembership() { return membership; }
    public void setMembership(Endpoint membership) { this.membership = membership; }
    public Endpoint getMonitoring() { return monitoring; }
    public void setMonitoring(Endpoint monitoring) { this.monitoring = monitoring; }
    public Endpoint getTicketing() { return ticketing; }
    public void setTicketing(Endpoint ticketing) { this.ticketing = ticketing; }
    public Endpoint getPim() { return pim; }
    public void setPim(Endpoint pim) { this.pim = pim; }
    public Endpoint getSrm() { return srm; }
    public void setSrm(Endpoint srm) { this.srm = srm; }
    public Endpoint getMes() { return mes; }
    public void setMes(Endpoint mes) { this.mes = mes; }
    public Endpoint getQms() { return qms; }
    public void setQms(Endpoint qms) { this.qms = qms; }
    public Endpoint getWms() { return wms; }
    public void setWms(Endpoint wms) { this.wms = wms; }
    public Endpoint getTms() { return tms; }
    public void setTms(Endpoint tms) { this.tms = tms; }

    public static class Endpoint {
        private boolean enabled;
        private String baseUrl = "";
        private String token = "";
        private String authHeader = "Authorization";
        private String authScheme = "Bearer";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getAuthHeader() { return authHeader; }
        public void setAuthHeader(String authHeader) { this.authHeader = authHeader; }
        public String getAuthScheme() { return authScheme; }
        public void setAuthScheme(String authScheme) { this.authScheme = authScheme; }
    }
}
