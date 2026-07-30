package com.example.supportops.module.analytics;

import com.example.supportops.module.analytics.dao.ServiceOperationsAnalyticsDAO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.DimensionPointVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.SummaryVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.TicketRowVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.TrendPointVO;
import com.example.supportops.module.analytics.service.ServiceOperationsAnalyticsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceOperationsAnalyticsServiceTests {
    private final LocalDate day = LocalDate.of(2026, 7, 24);

    @Test
    void returnsSnapshotWhenEveryAggregateMatchesTheDetailCohort() {
        ServiceOperationsAnalyticsDAO dao = consistentDao();
        var snapshot = new ServiceOperationsAnalyticsService(dao).snapshot(day, day, "", "", "");

        assertEquals(1, snapshot.summary().ticketTotal());
        assertEquals(1, snapshot.tickets().size());
        assertEquals(1, snapshot.statusDistribution().stream().mapToLong(DimensionPointVO::value).sum());
    }

    @Test
    void refusesToReturnSnapshotWhenAggregateAndDetailsDiverge() {
        ServiceOperationsAnalyticsDAO dao = consistentDao();
        when(dao.channelDistribution(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new DimensionPointVO("WEB", 2)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ServiceOperationsAnalyticsService(dao).snapshot(day, day, "", "", ""));
        assertTrue(error.getMessage().contains("渠道分布"));
    }

    private ServiceOperationsAnalyticsDAO consistentDao() {
        ServiceOperationsAnalyticsDAO dao = mock(ServiceOperationsAnalyticsDAO.class);
        when(dao.summary(any(), any(), anyString(), anyString(), anyString())).thenReturn(
                new SummaryVO(1, 1, 0, 1, 1, 1, 1, new BigDecimal("128.00"), BigDecimal.ZERO)
        );
        when(dao.trend(any(LocalDate.class), any(LocalDate.class), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new TrendPointVO(day, 1, 0, 1)));
        when(dao.statusDistribution(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new DimensionPointVO("OPEN", 1)));
        when(dao.priorityDistribution(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new DimensionPointVO("HIGH", 1)));
        when(dao.channelDistribution(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new DimensionPointVO("WEB", 1)));
        when(dao.scenarioDistribution(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new DimensionPointVO("ORDER_CANCELLED_BUT_CHARGED", 1)));
        when(dao.tickets(any(), any(), anyString(), anyString(), anyString())).thenReturn(List.of(
                new TicketRowVO(
                        "DEMO-TK-0724-001", "取消扣款客户", "O202607060002", "WEB",
                        "ORDER_CANCELLED_BUT_CHARGED", "OPEN", "HIGH",
                        LocalDateTime.of(2026, 7, 24, 8, 30),
                        LocalDateTime.of(2026, 7, 24, 8, 30)
                )
        ));
        return dao;
    }
}
