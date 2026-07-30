package com.example.supportops.module.business.integration;

import com.example.supportops.config.PlatformIntegrationProperties;
import com.example.supportops.infrastructure.integration.PlatformHttpClient;
import com.example.supportops.module.business.dao.BusinessQueryDAO;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurableBusinessDataSourceTests {

    @Test
    void disabledConnectorKeepsUsingLocalDatabase() {
        BusinessQueryDAO local = mock(BusinessQueryDAO.class);
        PlatformHttpClient remote = mock(PlatformHttpClient.class);
        PlatformIntegrationProperties properties = new PlatformIntegrationProperties();
        List<OrderRecord> expected = List.of();
        when(local.selectOrder("ORDER-1")).thenReturn(expected);

        ConfigurableBusinessDataSource source = new ConfigurableBusinessDataSource(local, remote, properties);

        assertSame(expected, source.order("ORDER-1"));
        verify(remote, never()).getList(eq("ERP"), eq(properties.getErp()),
                eq("/orders/ORDER-1"), anyMap(), eq(OrderRecord.class));
    }

    @Test
    void enabledLogisticsConnectorRoutesOnlyLogisticsToHttp() {
        BusinessQueryDAO local = mock(BusinessQueryDAO.class);
        PlatformHttpClient remote = mock(PlatformHttpClient.class);
        PlatformIntegrationProperties properties = new PlatformIntegrationProperties();
        properties.getLogistics().setEnabled(true);
        properties.getLogistics().setBaseUrl("https://logistics.example.test");
        List<LogisticsRecord> expected = List.of();
        when(remote.getList(eq("物流平台"), eq(properties.getLogistics()),
                eq("/orders/ORDER-1/logistics"), anyMap(), eq(LogisticsRecord.class))).thenReturn(expected);

        ConfigurableBusinessDataSource source = new ConfigurableBusinessDataSource(local, remote, properties);

        assertSame(expected, source.logisticsByOrder("ORDER-1"));
        verify(local, never()).selectLogisticsByOrder("ORDER-1");
    }
}
