package com.example.supportops.module.analytics.service;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.analytics.dao.ServiceOperationsAnalyticsDAO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.FilterVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.DimensionPointVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.SnapshotVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class ServiceOperationsAnalyticsService {
    private static final int MAX_RANGE_DAYS = 366;
    private final ServiceOperationsAnalyticsDAO dao;

    public ServiceOperationsAnalyticsService(ServiceOperationsAnalyticsDAO dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public SnapshotVO snapshot(
            LocalDate requestedFrom, LocalDate requestedTo, String channel, String priority, String status
    ) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "统计时间范围无效或超过366天");
        }
        String normalizedChannel = normalizeFilter(channel);
        String normalizedPriority = normalizeFilter(priority);
        String normalizedStatus = normalizeFilter(status);
        var fromTime = from.atStartOfDay();
        var toExclusive = to.plusDays(1).atStartOfDay();

        SnapshotVO snapshot = new SnapshotVO(
                new FilterVO(from, to, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.summary(fromTime, toExclusive, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.trend(from, to, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.statusDistribution(fromTime, toExclusive, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.priorityDistribution(fromTime, toExclusive, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.channelDistribution(fromTime, toExclusive, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.scenarioDistribution(fromTime, toExclusive, normalizedChannel, normalizedPriority, normalizedStatus),
                dao.tickets(fromTime, toExclusive, normalizedChannel, normalizedPriority, normalizedStatus),
                OffsetDateTime.now()
        );
        verifyConsistency(snapshot);
        return snapshot;
    }

    private void verifyConsistency(SnapshotVO snapshot) {
        long ticketTotal = snapshot.summary().ticketTotal();
        requireEqual("状态分布", ticketTotal, sum(snapshot.statusDistribution()));
        requireEqual("优先级分布", ticketTotal, sum(snapshot.priorityDistribution()));
        requireEqual("渠道分布", ticketTotal, sum(snapshot.channelDistribution()));
        requireEqual("场景分布", ticketTotal, sum(snapshot.scenarioDistribution()));
        requireEqual("工单明细", ticketTotal, snapshot.tickets().size());
        requireEqual("趋势新增工单", ticketTotal,
                snapshot.trend().stream().mapToLong(point -> point.createdTickets()).sum());
        requireEqual("待处理工单", snapshot.summary().pendingTickets(),
                dimensionSum(snapshot.statusDistribution(), "OPEN", "PROCESSING"));
        requireEqual("已解决工单", snapshot.summary().resolvedTickets(),
                dimensionSum(snapshot.statusDistribution(), "RESOLVED", "CLOSED"));
        requireEqual("高优先级工单", snapshot.summary().highPriorityTickets(),
                dimensionSum(snapshot.priorityDistribution(), "HIGH", "URGENT"));
        requireEqual("趋势退款申请", snapshot.summary().refundTotal(),
                snapshot.trend().stream().mapToLong(point -> point.refundRequests()).sum());
    }

    private long sum(java.util.List<DimensionPointVO> points) {
        return points.stream().mapToLong(DimensionPointVO::value).sum();
    }

    private long dimensionSum(java.util.List<DimensionPointVO> points, String... keys) {
        var accepted = java.util.Set.of(keys);
        return points.stream().filter(point -> accepted.contains(point.key()))
                .mapToLong(DimensionPointVO::value).sum();
    }

    private void requireEqual(String metric, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException("统计数据一致性校验失败：" + metric
                    + "，汇总值=" + expected + "，分项合计=" + actual);
        }
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
