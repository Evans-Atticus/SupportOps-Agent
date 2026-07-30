package com.example.supportops.module.analytics.dao;

import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.DimensionPointVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.SummaryVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.TicketRowVO;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.TrendPointVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ServiceOperationsAnalyticsDAO {
    private final JdbcTemplate jdbcTemplate;

    public ServiceOperationsAnalyticsDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SummaryVO summary(
            LocalDateTime from, LocalDateTime to, String channel, String priority, String status
    ) {
        TicketSummary ticket = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(status IN ('OPEN','PROCESSING')), 0) AS pending,
                       COALESCE(SUM(status IN ('RESOLVED','CLOSED')), 0) AS resolved,
                       COALESCE(SUM(priority IN ('HIGH','URGENT')), 0) AS high_priority_count
                  FROM tickets
                 WHERE created_at>=? AND created_at<?
                   AND (?='' OR channel=?)
                   AND (?='' OR priority=?)
                   AND (?='' OR status=?)
                """, (rs, rowNum) -> new TicketSummary(
                rs.getLong("total"),
                rs.getLong("pending"),
                rs.getLong("resolved"),
                rs.getLong("high_priority_count")
        ), from, to, channel, channel, priority, priority, status, status);

        RefundSummary refund = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(r.status IN ('SUBMITTED','UNDER_REVIEW','NEED_MORE_INFO')), 0) AS pending,
                       COALESCE(SUM(r.risk_level='HIGH'), 0) AS high_risk,
                       COALESCE(SUM(r.requested_amount), 0) AS requested_amount,
                       COALESCE(SUM(r.approved_amount), 0) AS approved_amount
                  FROM refund_requests r
                  LEFT JOIN tickets t ON t.id=r.ticket_id
                 WHERE r.created_at>=? AND r.created_at<?
                   AND (?='' OR t.channel=?)
                   AND (?='' OR t.priority=?)
                   AND (?='' OR t.status=?)
                """, (rs, rowNum) -> new RefundSummary(
                rs.getLong("total"),
                rs.getLong("pending"),
                rs.getLong("high_risk"),
                rs.getBigDecimal("requested_amount"),
                rs.getBigDecimal("approved_amount")
        ), from, to, channel, channel, priority, priority, status, status);

        TicketSummary safeTicket = ticket == null ? new TicketSummary(0, 0, 0, 0) : ticket;
        RefundSummary safeRefund = refund == null
                ? new RefundSummary(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO) : refund;
        return new SummaryVO(
                safeTicket.total(), safeTicket.pending(), safeTicket.resolved(), safeTicket.highPriority(),
                safeRefund.total(), safeRefund.pending(), safeRefund.highRisk(),
                safeRefund.requestedAmount(), safeRefund.approvedAmount()
        );
    }

    public List<DimensionPointVO> statusDistribution(
            LocalDateTime from, LocalDateTime to, String channel, String priority, String status
    ) {
        return ticketDimension("status", from, to, channel, priority, status);
    }

    public List<DimensionPointVO> priorityDistribution(
            LocalDateTime from, LocalDateTime to, String channel, String priority, String status
    ) {
        return ticketDimension("priority", from, to, channel, priority, status);
    }

    public List<DimensionPointVO> channelDistribution(
            LocalDateTime from, LocalDateTime to, String channel, String priority, String status
    ) {
        return ticketDimension("channel", from, to, channel, priority, status);
    }

    public List<DimensionPointVO> scenarioDistribution(
            LocalDateTime from, LocalDateTime to, String channel, String priority, String status
    ) {
        return jdbcTemplate.query("""
                SELECT COALESCE(scenario_hint, 'UNCLASSIFIED') AS dimension_key, COUNT(*) AS dimension_value
                  FROM tickets
                 WHERE created_at>=? AND created_at<?
                   AND (?='' OR channel=?)
                   AND (?='' OR priority=?)
                   AND (?='' OR status=?)
                 GROUP BY COALESCE(scenario_hint, 'UNCLASSIFIED')
                 ORDER BY dimension_value DESC, dimension_key
                """, (rs, rowNum) -> new DimensionPointVO(
                rs.getString("dimension_key"), rs.getLong("dimension_value")
        ), from, to, channel, channel, priority, priority, status, status);
    }

    public List<TrendPointVO> trend(
            LocalDate from, LocalDate to, String channel, String priority, String status
    ) {
        return jdbcTemplate.query("""
                WITH RECURSIVE calendar(day) AS (
                    SELECT CAST(? AS DATE)
                    UNION ALL
                    SELECT DATE_ADD(day, INTERVAL 1 DAY) FROM calendar WHERE day < CAST(? AS DATE)
                )
                SELECT c.day,
                       (SELECT COUNT(*) FROM tickets t
                         WHERE t.created_at>=c.day AND t.created_at<DATE_ADD(c.day, INTERVAL 1 DAY)
                           AND (?='' OR t.channel=?)
                           AND (?='' OR t.priority=?)
                           AND (?='' OR t.status=?)) AS created_tickets,
                       (SELECT COUNT(*) FROM tickets t
                         WHERE t.status IN ('RESOLVED','CLOSED')
                           AND t.created_at>=c.day AND t.created_at<DATE_ADD(c.day, INTERVAL 1 DAY)
                           AND (?='' OR t.channel=?)
                           AND (?='' OR t.priority=?)
                           AND (?='' OR t.status=?)) AS resolved_tickets,
                       (SELECT COUNT(*) FROM refund_requests r
                         LEFT JOIN tickets t ON t.id=r.ticket_id
                         WHERE r.created_at>=c.day AND r.created_at<DATE_ADD(c.day, INTERVAL 1 DAY)
                           AND (?='' OR t.channel=?)
                           AND (?='' OR t.priority=?)
                           AND (?='' OR t.status=?))
                         AS refund_requests
                  FROM calendar c
                 ORDER BY c.day
                """, (rs, rowNum) -> new TrendPointVO(
                rs.getDate("day").toLocalDate(),
                rs.getLong("created_tickets"),
                rs.getLong("resolved_tickets"),
                rs.getLong("refund_requests")
        ), from, to,
                channel, channel, priority, priority, status, status,
                channel, channel, priority, priority, status, status,
                channel, channel, priority, priority, status, status);
    }

    public List<TicketRowVO> tickets(
            LocalDateTime from, LocalDateTime to, String channel, String priority, String status
    ) {
        return jdbcTemplate.query("""
                SELECT t.ticket_no, c.customer_name, t.business_no, t.channel, t.scenario_hint,
                       t.status, t.priority, t.created_at, t.updated_at
                  FROM tickets t
                  JOIN customers c ON c.id=t.customer_id
                 WHERE t.created_at>=? AND t.created_at<?
                   AND (?='' OR t.channel=?)
                   AND (?='' OR t.priority=?)
                   AND (?='' OR t.status=?)
                 ORDER BY
                       CASE t.priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2
                                       WHEN 'NORMAL' THEN 3 ELSE 4 END,
                       t.created_at DESC
                """, (rs, rowNum) -> new TicketRowVO(
                rs.getString("ticket_no"),
                rs.getString("customer_name"),
                rs.getString("business_no"),
                rs.getString("channel"),
                rs.getString("scenario_hint"),
                rs.getString("status"),
                rs.getString("priority"),
                timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("updated_at"))
        ), from, to, channel, channel, priority, priority, status, status);
    }

    private List<DimensionPointVO> ticketDimension(
            String column, LocalDateTime from, LocalDateTime to,
            String channel, String priority, String status
    ) {
        if (!List.of("status", "priority", "channel").contains(column)) {
            throw new IllegalArgumentException("Unsupported analytics dimension");
        }
        String sql = """
                SELECT %s AS dimension_key, COUNT(*) AS dimension_value
                  FROM tickets
                 WHERE created_at>=? AND created_at<?
                   AND (?='' OR channel=?)
                   AND (?='' OR priority=?)
                   AND (?='' OR status=?)
                 GROUP BY %s
                 ORDER BY dimension_value DESC, dimension_key
                """.formatted(column, column);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DimensionPointVO(
                rs.getString("dimension_key"), rs.getLong("dimension_value")
        ), from, to, channel, channel, priority, priority, status, status);
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record TicketSummary(long total, long pending, long resolved, long highPriority) {
    }

    private record RefundSummary(
            long total,
            long pending,
            long highRisk,
            BigDecimal requestedAmount,
            BigDecimal approvedAmount
    ) {
    }
}
