package com.example.supportops.module.analytics.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class ServiceOperationsAnalyticsModels {
    private ServiceOperationsAnalyticsModels() {
    }

    public record FilterVO(LocalDate from, LocalDate to, String channel, String priority, String status) {
    }

    public record SummaryVO(
            long ticketTotal,
            long pendingTickets,
            long resolvedTickets,
            long highPriorityTickets,
            long refundTotal,
            long pendingRefunds,
            long highRiskRefunds,
            BigDecimal requestedRefundAmount,
            BigDecimal approvedRefundAmount
    ) {
    }

    public record DimensionPointVO(String key, long value) {
    }

    public record TrendPointVO(LocalDate date, long createdTickets, long resolvedTickets, long refundRequests) {
    }

    public record TicketRowVO(
            String ticketNo,
            String customerName,
            String businessNo,
            String channel,
            String scenario,
            String status,
            String priority,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record SnapshotVO(
            FilterVO filter,
            SummaryVO summary,
            List<TrendPointVO> trend,
            List<DimensionPointVO> statusDistribution,
            List<DimensionPointVO> priorityDistribution,
            List<DimensionPointVO> channelDistribution,
            List<DimensionPointVO> scenarioDistribution,
            List<TicketRowVO> tickets,
            OffsetDateTime generatedAt
    ) {
    }
}
