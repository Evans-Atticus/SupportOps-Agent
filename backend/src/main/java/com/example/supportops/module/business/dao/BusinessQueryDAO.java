package com.example.supportops.module.business.dao;

import com.example.supportops.module.business.model.query.ApiCallRecord;
import com.example.supportops.module.business.model.query.CouponRecord;
import com.example.supportops.module.business.model.query.CustomerRecord;
import com.example.supportops.module.business.model.query.InvoiceRecord;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.MemberRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.business.model.query.RefundRecord;
import com.example.supportops.module.business.model.query.SopRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BusinessQueryDAO {
    private final JdbcTemplate jdbcTemplate;

    public BusinessQueryDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CustomerRecord> selectCustomer(String customerNo) {
        return jdbcTemplate.query("""
                SELECT id, customer_no, customer_name, mobile_masked, email_masked, status, created_at, updated_at
                  FROM customers WHERE customer_no = ?
                """, (rs, rowNum) -> new CustomerRecord(
                rs.getLong("id"), rs.getString("customer_no"), rs.getString("customer_name"),
                rs.getString("mobile_masked"), rs.getString("email_masked"), rs.getString("status"),
                time(rs, "created_at"), time(rs, "updated_at")), customerNo);
    }

    public List<OrderRecord> selectOrder(String orderNo) {
        return jdbcTemplate.query("""
                SELECT o.id, o.order_no, c.customer_no, o.order_status, o.payment_status,
                       o.total_amount, o.payable_amount, o.currency, o.coupon_code, o.product_scope,
                       o.paid_at, o.cancelled_at, o.updated_at
                  FROM biz_orders o JOIN customers c ON c.id = o.customer_id
                 WHERE o.order_no = ?
                """, (rs, rowNum) -> new OrderRecord(
                rs.getLong("id"), rs.getString("order_no"), rs.getString("customer_no"),
                rs.getString("order_status"), rs.getString("payment_status"), rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("payable_amount"), rs.getString("currency"), rs.getString("coupon_code"),
                rs.getString("product_scope"), time(rs, "paid_at"), time(rs, "cancelled_at"),
                time(rs, "updated_at")), orderNo);
    }

    public List<PaymentRecord> selectPaymentsByOrder(String orderNo) {
        return jdbcTemplate.query("""
                SELECT p.id, p.payment_no, o.order_no, p.channel, p.payment_status, p.amount,
                       p.callback_status, p.callback_error_code, p.callback_attempts, p.paid_at, p.callback_at
                  FROM payment_records p JOIN biz_orders o ON o.id = p.order_id
                 WHERE o.order_no = ? ORDER BY p.created_at DESC
                """, (rs, rowNum) -> new PaymentRecord(
                rs.getLong("id"), rs.getString("payment_no"), rs.getString("order_no"),
                rs.getString("channel"), rs.getString("payment_status"), rs.getBigDecimal("amount"),
                rs.getString("callback_status"), rs.getString("callback_error_code"),
                integer(rs, "callback_attempts"), time(rs, "paid_at"), time(rs, "callback_at")), orderNo);
    }

    public List<RefundRecord> selectRefundsByOrder(String orderNo) {
        return jdbcTemplate.query("""
                SELECT r.id, r.refund_no, o.order_no, r.refund_status, r.refund_amount,
                       r.failure_code, r.requested_at, r.completed_at
                  FROM refund_records r JOIN biz_orders o ON o.id = r.order_id
                 WHERE o.order_no = ? ORDER BY r.created_at DESC
                """, (rs, rowNum) -> new RefundRecord(
                rs.getLong("id"), rs.getString("refund_no"), rs.getString("order_no"),
                rs.getString("refund_status"), rs.getBigDecimal("refund_amount"), rs.getString("failure_code"),
                time(rs, "requested_at"), time(rs, "completed_at")), orderNo);
    }

    public List<CouponRecord> selectCoupon(String couponCode, String customerNo) {
        return jdbcTemplate.query("""
                SELECT cp.id, cp.coupon_code, cp.coupon_name, cp.coupon_status, cp.threshold_amount,
                       cp.discount_amount, cp.product_scope, cp.valid_from, cp.valid_until,
                       cc.receive_status, cc.received_at, c.customer_no
                  FROM coupons cp
                  JOIN customer_coupons cc ON cc.coupon_id = cp.id
                  JOIN customers c ON c.id = cc.customer_id
                 WHERE cp.coupon_code = ? AND c.customer_no = ?
                """, (rs, rowNum) -> new CouponRecord(
                rs.getLong("id"), rs.getString("coupon_code"), rs.getString("coupon_name"),
                rs.getString("coupon_status"), rs.getBigDecimal("threshold_amount"),
                rs.getBigDecimal("discount_amount"), rs.getString("product_scope"), time(rs, "valid_from"),
                time(rs, "valid_until"), rs.getString("receive_status"), time(rs, "received_at"),
                rs.getString("customer_no")), couponCode, customerNo);
    }

    public List<MemberRecord> selectMember(String memberNo) {
        return jdbcTemplate.query("""
                SELECT m.id AS member_id, m.member_no, c.customer_no, m.member_level, m.member_status,
                       m.valid_from, m.valid_until, b.benefit_no, b.benefit_code, b.benefit_name,
                       b.grant_status, b.failure_code, b.expected_at, b.granted_at
                  FROM member_accounts m JOIN customers c ON c.id = m.customer_id
                  LEFT JOIN member_benefit_records b ON b.member_id = m.id
                 WHERE m.member_no = ? ORDER BY b.id
                """, (rs, rowNum) -> new MemberRecord(
                rs.getLong("member_id"), rs.getString("member_no"), rs.getString("customer_no"),
                rs.getString("member_level"), rs.getString("member_status"), time(rs, "valid_from"),
                time(rs, "valid_until"), rs.getString("benefit_no"), rs.getString("benefit_code"),
                rs.getString("benefit_name"), rs.getString("grant_status"), rs.getString("failure_code"),
                time(rs, "expected_at"), time(rs, "granted_at")), memberNo);
    }

    public List<LogisticsRecord> selectLogistics(String trackingNo) {
        return jdbcTemplate.query("""
                SELECT l.id, l.tracking_no, o.order_no, l.source_type, l.carrier_name, l.logistics_status,
                       l.status_description, l.origin_location, l.destination_location, l.current_location,
                       l.facility_name, l.courier_name_masked, l.courier_phone_masked,
                       l.estimated_delivery_at, l.event_time, l.synced_at
                  FROM logistics_records l JOIN biz_orders o ON o.id = l.order_id
                 WHERE l.tracking_no = ? ORDER BY l.event_time DESC
                """, (rs, rowNum) -> new LogisticsRecord(
                rs.getLong("id"), rs.getString("tracking_no"), rs.getString("order_no"),
                rs.getString("source_type"), rs.getString("carrier_name"), rs.getString("logistics_status"),
                rs.getString("status_description"), rs.getString("origin_location"),
                rs.getString("destination_location"), rs.getString("current_location"),
                rs.getString("facility_name"), rs.getString("courier_name_masked"),
                rs.getString("courier_phone_masked"), time(rs, "estimated_delivery_at"),
                time(rs, "event_time"), time(rs, "synced_at")), trackingNo);
    }

    /** 工单只保存订单号时，通过订单反查运单的本地与承运商快照。 */
    public List<LogisticsRecord> selectLogisticsByOrder(String orderNo) {
        return jdbcTemplate.query("""
                SELECT l.id, l.tracking_no, o.order_no, l.source_type, l.carrier_name, l.logistics_status,
                       l.status_description, l.origin_location, l.destination_location, l.current_location,
                       l.facility_name, l.courier_name_masked, l.courier_phone_masked,
                       l.estimated_delivery_at, l.event_time, l.synced_at
                  FROM logistics_records l JOIN biz_orders o ON o.id = l.order_id
                 WHERE o.order_no = ? ORDER BY l.event_time DESC
                """, (rs, rowNum) -> new LogisticsRecord(
                rs.getLong("id"), rs.getString("tracking_no"), rs.getString("order_no"),
                rs.getString("source_type"), rs.getString("carrier_name"), rs.getString("logistics_status"),
                rs.getString("status_description"), rs.getString("origin_location"),
                rs.getString("destination_location"), rs.getString("current_location"),
                rs.getString("facility_name"), rs.getString("courier_name_masked"),
                rs.getString("courier_phone_masked"), time(rs, "estimated_delivery_at"),
                time(rs, "event_time"), time(rs, "synced_at")), orderNo);
    }

    public List<ApiCallRecord> selectApiCalls(String clientCode, String apiName, int limit) {
        return jdbcTemplate.query("""
                SELECT id, client_code, api_name, trace_id, request_status, http_status,
                       error_code, duration_ms, called_at
                  FROM api_call_records
                 WHERE client_code = ? AND (? IS NULL OR api_name = ?)
                 ORDER BY called_at DESC LIMIT ?
                """, (rs, rowNum) -> new ApiCallRecord(
                rs.getLong("id"), rs.getString("client_code"), rs.getString("api_name"),
                rs.getString("trace_id"), rs.getString("request_status"), integer(rs, "http_status"),
                rs.getString("error_code"), integer(rs, "duration_ms"), time(rs, "called_at")),
                clientCode, apiName, apiName, limit);
    }

    public List<InvoiceRecord> selectInvoicesByOrder(String orderNo) {
        return jdbcTemplate.query("""
                SELECT i.id, i.invoice_no, o.order_no, i.invoice_type, i.title, i.tax_no,
                       i.email_masked, i.qualification_status, i.issue_status, i.failure_code, i.issued_at
                  FROM invoice_applications i JOIN biz_orders o ON o.id = i.order_id
                 WHERE o.order_no = ? ORDER BY i.created_at DESC
                """, (rs, rowNum) -> new InvoiceRecord(
                rs.getLong("id"), rs.getString("invoice_no"), rs.getString("order_no"),
                rs.getString("invoice_type"), rs.getString("title"), rs.getString("tax_no"),
                rs.getString("email_masked"), rs.getString("qualification_status"),
                rs.getString("issue_status"), rs.getString("failure_code"), time(rs, "issued_at")), orderNo);
    }

    public List<SopRecord> selectSop(String scenarioType) {
        return jdbcTemplate.query("""
                SELECT id, scenario_type, title, audience, version, content_json, enabled, updated_at
                  FROM sop_definitions
                 WHERE scenario_type = ? AND enabled = 1 ORDER BY version DESC LIMIT 1
                """, (rs, rowNum) -> new SopRecord(
                rs.getLong("id"), rs.getString("scenario_type"), rs.getString("title"),
                rs.getString("audience"), integer(rs, "version"), rs.getString("content_json"),
                bool(rs, "enabled"), time(rs, "updated_at")), scenarioType);
    }

    private static LocalDateTime time(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Integer integer(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static Boolean bool(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return ((Number) value).intValue() != 0;
    }
}
