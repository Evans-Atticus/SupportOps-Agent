package com.example.supportops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class SystemControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthUsesUnifiedEnvelopeAndRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/health").header("X-Request-Id", "test-request-0001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-request-0001"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value("test-request-0001"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.javaVersion").value(21));
    }

    @Test
    void loginEndpointRejectsBrowserGetAsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login").header("X-Request-Id", "test-request-0002"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").value("test-request-0002"));
    }

    @Test
    void loginEndpointRejectsQueryParametersWithoutJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .queryParam("username", "demo")
                        .queryParam("password", "SupportOps@2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "test-request-0003"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("请求体必须是有效的 JSON"));
    }

    @Test
    @WithMockUser(username = "support01", roles = "SUPPORT_AGENT")
    void supportAgentCannotAccessAiDiagnosisApi() throws Exception {
        mockMvc.perform(get("/api/v1/diagnoses").queryParam("limit", "1")
                        .header("X-Request-Id", "test-request-ai-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("test-request-ai-forbidden"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void erpKnowledgeSyncRequiresSourceReference() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/integrations/erp/sync/product-knowledge")
                        .file("file", "sample".getBytes())
                        .param("orderNo", "ORDER-1")
                        .param("documentType", "PRODUCT_MANUAL")
                        .header("X-Request-Id", "test-request-erp-required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("缺少必填参数: sourceReference"))
                .andExpect(jsonPath("$.requestId").value("test-request-erp-required"));
    }
}
