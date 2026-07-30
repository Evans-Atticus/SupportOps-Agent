package com.example.supportops.infrastructure.integration;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.config.PlatformIntegrationProperties.Endpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 平台 HTTP 通信的公共安全边界：统一超时、认证头和错误转换，不记录 Token 或响应原文。
 */
@Component
public class PlatformHttpClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PlatformHttpClient(ObjectMapper objectMapper,
                              @Value("${supportops.integrations.connect-timeout:3s}") Duration connectTimeout,
                              @Value("${supportops.integrations.read-timeout:8s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    public <T> List<T> getList(String platform, Endpoint endpoint, String path,
                               Map<String, ?> query, Class<T> elementType) {
        JsonNode payload = exchange(platform, endpoint, path, query);
        JsonNode records = unwrap(payload);
        if (!records.isArray()) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE,
                    platform + " 响应必须是数组或 data/records 数组");
        }
        return objectMapper.convertValue(records,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public <T> T getObject(String platform, Endpoint endpoint, String path,
                           Map<String, ?> query, Class<T> responseType) {
        JsonNode payload = unwrap(exchange(platform, endpoint, path, query));
        try {
            return objectMapper.treeToValue(payload, responseType);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, platform + " 响应格式不匹配");
        }
    }

    private JsonNode exchange(String platform, Endpoint endpoint, String path, Map<String, ?> query) {
        requireConfigured(platform, endpoint);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(trimSlash(endpoint.getBaseUrl()) + path);
        query.forEach((name, value) -> {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                uriBuilder.queryParam(name, UriUtils.encodeQueryParam(String.valueOf(value), StandardCharsets.UTF_8));
            }
        });
        // 路径段和查询值已分别编码，避免业务号中的特殊字符改变请求结构。
        URI uri = uriBuilder.build(true).toUri();
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uri);
            if (StringUtils.hasText(endpoint.getToken())) {
                String scheme = StringUtils.hasText(endpoint.getAuthScheme()) ? endpoint.getAuthScheme().trim() + " " : "";
                request = request.header(endpoint.getAuthHeader(), scheme + endpoint.getToken());
            }
            JsonNode body = request.retrieve().body(JsonNode.class);
            if (body == null) throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, platform + " 返回空响应");
            return body;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // 只向上层透出平台名，避免第三方响应体携带敏感信息。
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, platform + " 请求失败");
        }
    }

    private JsonNode unwrap(JsonNode payload) {
        if (payload.has("data")) payload = payload.get("data");
        if (payload != null && payload.has("records")) payload = payload.get("records");
        return payload;
    }

    private void requireConfigured(String platform, Endpoint endpoint) {
        if (!endpoint.isEnabled() || !StringUtils.hasText(endpoint.getBaseUrl())) {
            throw new BusinessException(ErrorCode.INTEGRATION_NOT_CONFIGURED, platform + " 尚未配置");
        }
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
