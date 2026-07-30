package com.example.supportops.module.integration.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedExternalSyncServiceTests {
    private final ReservedExternalSyncService service = new ReservedExternalSyncService();

    @Test
    void erpReservationDoesNotPretendThatSynchronizationSucceeded() {
        var result = service.syncErpOrdersAndTickets("admin");

        assertThat(result.integration()).isEqualTo("ERP");
        assertThat(result.resourceType()).isEqualTo("ORDERS_AND_TICKETS");
        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.configured()).isFalse();
        assertThat(result.message()).contains("未执行同步");
    }

    @Test
    void wmsReservationUsesLogisticsTerminology() {
        var result = service.syncWmsLogistics("admin");

        assertThat(result.integration()).isEqualTo("WMS");
        assertThat(result.resourceType()).isEqualTo("LOGISTICS");
        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.configured()).isFalse();
        assertThat(result.message()).contains("物流信息").doesNotContain("物料");
    }
}
