package com.example.supportops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@WithMockUser(username = "trace-tester")
class TraceControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void queriesPurchaseAndInspectionByBusinessDocumentNumber() throws Exception {
        mockMvc.perform(get("/api/v1/trace/purchases").queryParam("purchaseNo", "PO-20260718-102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].purchaseNo").value("PO-20260718-102"));
        mockMvc.perform(get("/api/v1/trace/quality").queryParam("inspectionNo", "QC-0718-206"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].result").value("不合格"));
    }

    @Test
    void createsInboundOrderAndReturnsServerGeneratedTraceCode() throws Exception {
        String body = """
                {"sourcePurchaseNo":"PO-20260701-036","productCode":"SKU-A018",
                 "batchNo":"LOT-20260705-A18","warehouse":"华东一号仓","location":"A-08-16",
                 "quantity":100,"inboundType":"采购入库","remark":"接口测试"}
                """;
        mockMvc.perform(post("/api/v1/trace/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.referenceNo").value(org.hamcrest.Matchers.startsWith("IN-20260722-")))
                .andExpect(jsonPath("$.data.traceCode").value(org.hamcrest.Matchers.startsWith("TR1-")))
                .andExpect(jsonPath("$.data.status").value("已入库"));
    }

    @Test
    void resolvesTraceCodeToTrustedLifecycleEvents() throws Exception {
        mockMvc.perform(get("/api/v1/trace/search/SN-A018-00462"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("SKU-A018"))
                .andExpect(jsonPath("$.data.events.length()").value(6));
    }
}
