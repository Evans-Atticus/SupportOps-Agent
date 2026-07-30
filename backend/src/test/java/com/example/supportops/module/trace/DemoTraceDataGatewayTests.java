package com.example.supportops.module.trace;

import com.example.supportops.module.trace.integration.DemoTraceDataGateway;
import com.example.supportops.module.trace.integration.TraceCodeGenerator;
import com.example.supportops.module.trace.model.TraceModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoTraceDataGatewayTests {
    @Test
    void createsInboundOrderWithServerGeneratedTraceCodeAndMakesItSearchable() {
        DemoTraceDataGateway gateway = new DemoTraceDataGateway(new TraceCodeGenerator());
        TraceModels.Inventory created = gateway.createInbound(new TraceModels.InboundOrderCreate(
                null, "PO-20260701-036", "SKU-A018", "LOT-20260705-A18",
                "华东一号仓", "A-08-16", 100, "采购入库", "自动化测试"));

        assertTrue(created.referenceNo().startsWith("IN-20260722-"));
        assertTrue(created.traceCode().matches("TR1-[23456789A-HJ-NP-Z]{12}-\\d{2}"));
        assertEquals(created.referenceNo(), gateway.inventory(created.referenceNo(), null).get(0).referenceNo());
        assertEquals(created.traceCode(), gateway.trace(created.traceCode()).traceCode());
    }

    @Test
    void filtersEachBusinessListByItsOwnDocumentNumber() {
        DemoTraceDataGateway gateway = new DemoTraceDataGateway(new TraceCodeGenerator());

        assertEquals("PO-20260718-102", gateway.purchases("PO-20260718-102", null).get(0).purchaseNo());
        assertEquals("QC-0718-206", gateway.inspections("QC-0718-206", null).get(0).inspectionNo());
        assertEquals("OUT-20260712-018", gateway.inventory("OUT-20260712-018", null).get(0).referenceNo());
        assertEquals("TK-QC-206", gateway.tickets("TK-QC-206", null).get(0).ticketNo());
    }
}
