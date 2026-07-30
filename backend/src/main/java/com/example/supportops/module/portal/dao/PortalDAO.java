package com.example.supportops.module.portal.dao;

import com.example.supportops.module.portal.model.query.PortalQueryRecords.DashboardCountsRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ItemRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.LogisticsTimelineRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.RefundRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.UserContextRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.AvailableAgentRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationAttachmentRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationContextRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationMessageRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.CustomerConversationRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class PortalDAO {
    private final JdbcTemplate jdbcTemplate;

    public PortalDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserContextRecord findContext(String username) {
        return jdbcTemplate.query("""
                SELECT u.id, u.role_code, ca.customer_id
                  FROM support_users u
                  LEFT JOIN customer_accounts ca ON ca.user_id=u.id
                 WHERE u.username=? AND u.status='ACTIVE'
                """, rs -> rs.next() ? new UserContextRecord(
                rs.getLong("id"), rs.getString("role_code"), nullableLong(rs, "customer_id")) : null, username);
    }

    public DashboardCountsRecord customerDashboard(long customerId, long userId) {
        return new DashboardCountsRecord(
                count("SELECT COUNT(*) FROM biz_orders WHERE customer_id=?", customerId),
                count("SELECT COUNT(*) FROM tickets WHERE customer_id=? AND status NOT IN ('RESOLVED','CLOSED')",
                        customerId),
                count("""
                        SELECT COUNT(*) FROM refund_requests r JOIN biz_orders o ON o.id=r.order_id
                         WHERE o.customer_id=? AND r.status NOT IN ('SUCCEEDED','REJECTED','CANCELLED')
                        """, customerId),
                count("SELECT COUNT(*) FROM portal_notifications WHERE recipient_user_id=? AND read_flag=0", userId));
    }

    public DashboardCountsRecord agentDashboard(long userId) {
        return new DashboardCountsRecord(
                count("SELECT COUNT(*) FROM support_conversations WHERE assigned_user_id=? AND service_mode='AGENT_SERVING'",
                        userId),
                count("SELECT COUNT(*) FROM tickets WHERE status IN ('OPEN','PROCESSING')"),
                count("SELECT COUNT(*) FROM refund_requests WHERE status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','EXECUTING')"),
                count("SELECT COUNT(*) FROM diagnosis_tasks WHERE requested_by=?", userId));
    }

    public DashboardCountsRecord adminDashboard() {
        return new DashboardCountsRecord(
                count("SELECT COUNT(*) FROM support_users WHERE role_code='SUPPORT_AGENT' AND status='ACTIVE'"),
                count("SELECT COUNT(*) FROM tickets WHERE status IN ('OPEN','PROCESSING')"),
                count("SELECT COUNT(*) FROM refund_requests WHERE status IN ('SUBMITTED','UNDER_REVIEW')"),
                count("""
                        SELECT COUNT(DISTINCT tracking_no) FROM logistics_records
                         WHERE logistics_status IN ('FAILED','EXCEPTION')
                            OR (source_type='LOCAL' AND synced_at IS NULL)
                        """));
    }

    public List<ItemRecord> selectPeople(String keyword) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT CAST(id AS CHAR) AS item_id, display_name AS title,
                       CONCAT(username, ' · ', role_code) AS detail,
                       CONCAT('额度 ', daily_quota) AS meta, status, updated_at AS occurred_at
                  FROM support_users
                 WHERE role_code='SUPPORT_AGENT'
                   AND (?='' OR CAST(id AS CHAR) LIKE ? OR username LIKE ? OR display_name LIKE ? OR status LIKE ?)
                 ORDER BY status, updated_at DESC LIMIT 100
                """, PortalDAO::item, keyword, like, like, like, like);
    }

    public List<ItemRecord> selectTickets(String keyword, Long customerId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT t.ticket_no AS item_id,
                       CONCAT(t.ticket_no, ' · ', COALESCE(t.business_no, '无业务号')) AS title,
                       CONCAT(c.customer_name, ' · ', t.description) AS detail,
                       CONCAT(t.priority, ' · ', t.channel) AS meta, t.status, t.updated_at AS occurred_at
                  FROM tickets t JOIN customers c ON c.id=t.customer_id
                 WHERE (? IS NULL OR t.customer_id=?)
                   AND (?='' OR t.ticket_no LIKE ? OR c.customer_name LIKE ?
                        OR COALESCE(t.business_no,'') LIKE ? OR t.status LIKE ? OR t.description LIKE ?)
                 ORDER BY t.updated_at DESC LIMIT 100
                """, PortalDAO::item, customerId, customerId, keyword, like, like, like, like, like);
    }

    public List<ItemRecord> selectOrders(String keyword, Long customerId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT o.order_no AS item_id,
                       CONCAT(o.order_no, ' · ', COALESCE(o.product_scope,'未登记商品')) AS title,
                       CONCAT(c.customer_name, ' · 支付 ', o.payment_status) AS detail,
                       CONCAT(o.currency, ' ', o.payable_amount) AS meta, o.order_status AS status,
                       o.updated_at AS occurred_at
                  FROM biz_orders o JOIN customers c ON c.id=o.customer_id
                 WHERE (? IS NULL OR o.customer_id=?)
                   AND (?='' OR o.order_no LIKE ? OR c.customer_name LIKE ?
                        OR COALESCE(o.product_scope,'') LIKE ? OR o.order_status LIKE ?)
                 ORDER BY o.updated_at DESC LIMIT 100
                """, PortalDAO::item, customerId, customerId, keyword, like, like, like, like);
    }

    public List<ItemRecord> selectLogistics(String keyword, Long customerId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT l.tracking_no AS item_id,
                       CONCAT(l.tracking_no, ' · 订单 ', o.order_no) AS title,
                       CONCAT(c.customer_name, ' · ', COALESCE(o.product_scope,'未登记商品'),
                              ' · ', COALESCE(l.current_location, l.facility_name, '位置待更新'),
                              ' · ', COALESCE(l.status_description,'物流节点')) AS detail,
                       COALESCE(l.carrier_name, '承运商待更新') AS meta, l.logistics_status AS status,
                       l.event_time AS occurred_at
                  FROM logistics_records l
                  JOIN biz_orders o ON o.id=l.order_id
                  JOIN customers c ON c.id=o.customer_id
                 WHERE (? IS NULL OR o.customer_id=?)
                   AND l.id=(SELECT l2.id FROM logistics_records l2
                              WHERE l2.tracking_no=l.tracking_no
                              ORDER BY CASE WHEN l2.source_type='CARRIER' THEN 0 ELSE 1 END,
                                       l2.event_time DESC, l2.id DESC LIMIT 1)
                   AND (?='' OR l.tracking_no LIKE ? OR o.order_no LIKE ? OR c.customer_name LIKE ?
                        OR COALESCE(o.product_scope,'') LIKE ? OR l.logistics_status LIKE ?
                        OR COALESCE(l.status_description,'') LIKE ? OR COALESCE(l.current_location,'') LIKE ?
                        OR COALESCE(l.carrier_name,'') LIKE ?)
                 ORDER BY occurred_at DESC LIMIT 100
                """, PortalDAO::item, customerId, customerId, keyword, like, like, like, like, like, like, like, like);
    }

    public List<LogisticsTimelineRecord> selectLogisticsTimeline(String trackingNo, Long customerId) {
        return jdbcTemplate.query("""
                SELECT l.id, l.tracking_no, o.order_no, c.customer_name,
                       COALESCE(o.product_scope,'未登记商品') AS product, l.source_type,
                       l.carrier_name, l.logistics_status, l.status_description,
                       l.origin_location, l.destination_location, l.current_location, l.facility_name,
                       l.courier_name_masked, l.courier_phone_masked, l.estimated_delivery_at,
                       l.event_time, l.synced_at
                  FROM logistics_records l
                  JOIN biz_orders o ON o.id=l.order_id
                  JOIN customers c ON c.id=o.customer_id
                 WHERE l.tracking_no=? AND (? IS NULL OR o.customer_id=?)
                 ORDER BY l.event_time DESC, l.id DESC
                """, (rs, rowNum) -> new LogisticsTimelineRecord(
                rs.getLong("id"), rs.getString("tracking_no"), rs.getString("order_no"),
                rs.getString("customer_name"), rs.getString("product"), rs.getString("source_type"),
                rs.getString("carrier_name"), rs.getString("logistics_status"),
                rs.getString("status_description"), rs.getString("origin_location"),
                rs.getString("destination_location"), rs.getString("current_location"),
                rs.getString("facility_name"), rs.getString("courier_name_masked"),
                rs.getString("courier_phone_masked"), time(rs, "estimated_delivery_at"),
                time(rs, "event_time"), time(rs, "synced_at")), trackingNo, customerId, customerId);
    }

    public List<ItemRecord> selectConversations(String keyword, long assignedUserId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT sc.conversation_no AS item_id,
                       CONCAT(sc.conversation_no, ' · ', COALESCE(NULLIF(cu.display_name,''), '客户')) AS title,
                       COALESCE(sc.summary,'暂无摘要') AS detail, sc.risk_level AS meta,
                       sc.service_mode AS status, sc.last_message_at AS occurred_at
                  FROM support_conversations sc
                  JOIN customers c ON c.id=sc.customer_id
                  LEFT JOIN customer_accounts ca ON ca.customer_id=c.id
                 LEFT JOIN support_users cu ON cu.id=ca.user_id
                 WHERE (sc.assigned_user_id=? OR sc.service_mode='WAITING_AGENT')
                   AND sc.agent_archived_at IS NULL
                   AND (?='' OR sc.conversation_no LIKE ? OR COALESCE(cu.display_name,'客户') LIKE ?
                        OR COALESCE(sc.summary,'') LIKE ? OR sc.service_mode LIKE ?)
                 ORDER BY sc.last_message_at DESC LIMIT 100
                """, PortalDAO::item, assignedUserId, keyword, like, like, like, like);
    }

    public List<ItemRecord> selectDiagnoses(String keyword, long requestedBy, boolean all) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT CAST(d.id AS CHAR) AS item_id,
                       CONCAT('D-', LPAD(d.id, 6, '0'), ' · 工单 ', t.ticket_no) AS title,
                       CONCAT(COALESCE(d.scenario_type,'待识别'), ' · ', COALESCE(r.summary,'暂无报告')) AS detail,
                       COALESCE(d.confidence,0) AS meta, d.status, d.updated_at AS occurred_at
                  FROM diagnosis_tasks d JOIN tickets t ON t.id=d.ticket_id
                  LEFT JOIN diagnosis_reports r ON r.diagnosis_id=d.id
                 WHERE (?=1 OR d.requested_by=?)
                   AND (?='' OR CAST(d.id AS CHAR) LIKE ? OR t.ticket_no LIKE ?
                        OR COALESCE(d.scenario_type,'') LIKE ? OR d.status LIKE ?)
                 ORDER BY d.updated_at DESC LIMIT 100
                """, PortalDAO::item, all, requestedBy, keyword, like, like, like, like);
    }

    public List<ItemRecord> selectMessages(String keyword, long userId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT CAST(id AS CHAR) AS item_id, title, content AS detail,
                       COALESCE(related_no, notification_type) AS meta,
                       IF(read_flag=1,'READ','UNREAD') AS status, created_at AS occurred_at
                  FROM portal_notifications
                 WHERE recipient_user_id=?
                   AND (?='' OR CAST(id AS CHAR) LIKE ? OR title LIKE ? OR content LIKE ?
                        OR COALESCE(related_no,'') LIKE ?)
                 ORDER BY created_at DESC LIMIT 100
                """, PortalDAO::item, userId, keyword, like, like, like, like);
    }

    public List<ItemRecord> selectAudit(String keyword) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT CAST(a.id AS CHAR) AS item_id, u.display_name AS title,
                       CONCAT(a.action_code, ' · ', COALESCE(a.detail,'')) AS detail,
                       CONCAT(a.resource_type, ' ', a.resource_no, ' · 请求 ',
                              COALESCE(a.request_id,'--')) AS meta,
                       a.result AS status, a.created_at AS occurred_at
                  FROM portal_audit_logs a JOIN support_users u ON u.id=a.actor_user_id
                 WHERE (?='' OR CAST(a.id AS CHAR) LIKE ? OR u.display_name LIKE ? OR a.action_code LIKE ?
                        OR a.resource_no LIKE ? OR COALESCE(a.request_id,'') LIKE ?)
                 ORDER BY a.created_at DESC LIMIT 100
                """, PortalDAO::item, keyword, like, like, like, like, like);
    }

    public List<ItemRecord> selectTicketStatistics(String keyword) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT status AS item_id, status AS title,
                       CONCAT('当前状态工单数量 ', COUNT(*)) AS detail,
                       CONCAT('高优先级 ', SUM(priority IN ('HIGH','URGENT'))) AS meta,
                       'METRIC' AS status, MAX(updated_at) AS occurred_at
                  FROM tickets
                 WHERE (?='' OR status LIKE ? OR priority LIKE ? OR channel LIKE ?)
                 GROUP BY status ORDER BY COUNT(*) DESC
                """, PortalDAO::item, keyword, like, like, like);
    }

    public List<ItemRecord> selectOverview(String keyword) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT item_id, title, detail, meta, status, occurred_at
                  FROM (
                    SELECT 'TICKETS' AS item_id, '待处理工单' AS title,
                           CONCAT('当前数量 ', COUNT(*)) AS detail,
                           CONCAT('高优先级 ', SUM(priority IN ('HIGH','URGENT'))) AS meta,
                           'METRIC' AS status, MAX(updated_at) AS occurred_at
                      FROM tickets WHERE status IN ('OPEN','PROCESSING')
                    UNION ALL
                    SELECT 'REFUNDS', '待审批退款', CONCAT('当前数量 ', COUNT(*)),
                           CONCAT('高风险 ', SUM(risk_level='HIGH')), 'METRIC', MAX(updated_at)
                      FROM refund_requests WHERE status IN ('SUBMITTED','UNDER_REVIEW')
                    UNION ALL
                    SELECT 'LOGISTICS', '物流异常', CONCAT('异常节点 ', COUNT(*)),
                           '运单与订单关联核验', 'METRIC', MAX(event_time)
                      FROM logistics_records
                     WHERE logistics_status IN ('FAILED','EXCEPTION')
                        OR (source_type='LOCAL' AND synced_at IS NULL)
                    UNION ALL
                    SELECT 'PEOPLE', '客服人员', CONCAT('启用账号 ', COUNT(*)),
                           '人员与权限管理', 'METRIC', MAX(updated_at)
                      FROM support_users WHERE role_code='SUPPORT_AGENT' AND status='ACTIVE'
                  ) overview_rows
                 WHERE (?='' OR item_id LIKE ? OR title LIKE ? OR detail LIKE ? OR meta LIKE ?)
                 ORDER BY occurred_at DESC
                """, PortalDAO::item, keyword, like, like, like, like);
    }

    public List<ItemRecord> selectAgentSettings(String keyword) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT call_type AS item_id, call_type AS title,
                       CONCAT('模型 ', GROUP_CONCAT(DISTINCT model_name SEPARATOR ', '),
                              ' · 调用 ', COUNT(*), ' 次') AS detail,
                       CONCAT('平均耗时 ', ROUND(AVG(duration_ms)), 'ms') AS meta,
                       IF(SUM(call_status='FAILED')>0,'ATTENTION','NORMAL') AS status,
                       MAX(created_at) AS occurred_at
                  FROM model_call_logs
                 WHERE (?='' OR call_type LIKE ? OR model_name LIKE ? OR call_status LIKE ?)
                 GROUP BY call_type ORDER BY occurred_at DESC
                """, PortalDAO::item, keyword, like, like, like);
    }

    public List<ItemRecord> selectCustomerProfile(String keyword, long customerId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT c.customer_no AS item_id, '基本资料' AS title,
                       CONCAT(c.customer_name, ' · 客户编号 ', c.customer_no) AS detail,
                       COALESCE(c.mobile_masked,'未绑定手机') AS meta,
                       c.status, c.updated_at AS occurred_at
                  FROM customers c
                 WHERE c.id=?
                   AND (?='' OR c.customer_no LIKE ? OR c.customer_name LIKE ?
                        OR COALESCE(c.mobile_masked,'') LIKE ? OR COALESCE(c.email_masked,'') LIKE ?
                        OR c.status LIKE ?)
                UNION ALL
                SELECT CONCAT(c.customer_no, '-CONTACT') AS item_id, '联系方式' AS title,
                       CONCAT('手机 ', COALESCE(c.mobile_masked,'未绑定'),
                              ' · 邮箱 ', COALESCE(c.email_masked,'未绑定')) AS detail,
                       '隐私信息已脱敏' AS meta, 'SECURE' AS status, c.updated_at AS occurred_at
                  FROM customers c
                 WHERE c.id=?
                   AND (?='' OR '联系方式' LIKE ? OR COALESCE(c.mobile_masked,'') LIKE ?
                        OR COALESCE(c.email_masked,'') LIKE ?)
                 ORDER BY occurred_at DESC
                """, PortalDAO::item, customerId, keyword, like, like, like, like, like,
                customerId, keyword, like, like, like);
    }

    public List<RefundRecord> selectRefunds(String keyword, Long customerId) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT r.id, r.refund_no, c.customer_name, o.order_no, o.product_scope,
                       o.payable_amount AS order_amount, r.requested_amount, r.approved_amount,
                       r.status, r.risk_level, r.risk_message, r.refund_channel, r.reject_reason,
                       r.created_at AS requested_at, r.reviewed_at, r.expected_arrival_at, r.completed_at
                  FROM refund_requests r
                  JOIN biz_orders o ON o.id=r.order_id
                  JOIN customers c ON c.id=o.customer_id
                 WHERE (? IS NULL OR o.customer_id=?)
                   AND (?='' OR r.refund_no LIKE ? OR c.customer_name LIKE ? OR o.order_no LIKE ?
                        OR COALESCE(o.product_scope,'') LIKE ? OR r.status LIKE ?
                        OR r.risk_level LIKE ? OR COALESCE(r.refund_channel,'') LIKE ?)
                 ORDER BY r.created_at DESC LIMIT 100
                """, PortalDAO::refund, customerId, customerId, keyword,
                like, like, like, like, like, like, like);
    }

    public RefundRecord findRefund(String refundNo) {
        List<RefundRecord> rows = jdbcTemplate.query("""
                SELECT r.id, r.refund_no, c.customer_name, o.order_no, o.product_scope,
                       o.payable_amount AS order_amount, r.requested_amount, r.approved_amount,
                       r.status, r.risk_level, r.risk_message, r.refund_channel, r.reject_reason,
                       r.created_at AS requested_at, r.reviewed_at, r.expected_arrival_at, r.completed_at
                  FROM refund_requests r JOIN biz_orders o ON o.id=r.order_id
                  JOIN customers c ON c.id=o.customer_id WHERE r.refund_no=?
                """, PortalDAO::refund, refundNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean createRefund(String refundNo, String orderNo, String ticketNo, long requesterId,
                                BigDecimal amount, String reason, String channel) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO refund_requests
                      (refund_no, order_id, ticket_id, requested_by, requested_amount, status,
                       reason, risk_level, risk_message, refund_channel)
                    SELECT ?, o.id, t.id, ?, ?, 'SUBMITTED', ?, 'LOW',
                           '尚未发现重复退款，等待人工核验。', ?
                      FROM biz_orders o LEFT JOIN tickets t ON t.ticket_no=?
                     WHERE o.order_no=?
                    """, refundNo, requesterId, amount, reason, channel, ticketNo, orderNo) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean orderBelongsToCustomer(String orderNo, long customerId) {
        return count("SELECT COUNT(*) FROM biz_orders WHERE order_no=? AND customer_id=?",
                orderNo, customerId) == 1;
    }

    public int approveRefund(long id, BigDecimal amount, long reviewerId) {
        return jdbcTemplate.update("""
                UPDATE refund_requests SET approved_amount=?, status='APPROVED',
                       reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP(3), reject_reason=NULL
                 WHERE id=? AND status IN ('SUBMITTED','UNDER_REVIEW','NEED_MORE_INFO')
                """, amount, reviewerId, id);
    }

    public int rejectRefund(long id, String reason, long reviewerId) {
        return jdbcTemplate.update("""
                UPDATE refund_requests SET status='REJECTED', reject_reason=?,
                       reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP(3)
                 WHERE id=? AND status IN ('SUBMITTED','UNDER_REVIEW','NEED_MORE_INFO')
                """, reason, reviewerId, id);
    }

    public int executeRefund(long id) {
        return jdbcTemplate.update("""
                UPDATE refund_requests SET status='EXECUTING', executed_at=CURRENT_TIMESTAMP(3),
                       expected_arrival_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 3 DAY)
                 WHERE id=? AND status='APPROVED'
                """, id);
    }

    public void insertAudit(long actorId, String action, String resourceNo, String detail, String requestId) {
        jdbcTemplate.update("""
                INSERT INTO portal_audit_logs
                  (actor_user_id, action_code, resource_type, resource_no, result, detail, request_id)
                VALUES (?, ?, 'REFUND', ?, 'SUCCESS', ?, ?)
                """, actorId, action, resourceNo, detail, requestId);
    }

    public void insertNotificationForRefund(long refundId, String title, String content) {
        jdbcTemplate.update("""
                INSERT INTO portal_notifications
                  (recipient_user_id, notification_type, title, content, related_type, related_no)
                SELECT ca.user_id, 'REFUND_STATUS', ?, ?, 'REFUND', r.refund_no
                  FROM refund_requests r JOIN biz_orders o ON o.id=r.order_id
                  JOIN customer_accounts ca ON ca.customer_id=o.customer_id
                 WHERE r.id=?
                """, title, content, refundId);
    }

    public void insertSupportUser(String username, String passwordHash, String displayName) {
        jdbcTemplate.update("""
                INSERT INTO support_users
                  (username, password_hash, display_name, role_code, status, daily_quota)
                VALUES (?, ?, ?, 'SUPPORT_AGENT', 'ACTIVE', 50)
                """, username, passwordHash, displayName);
    }

    public boolean supportUsernameExistsForOther(String username, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM support_users
                 WHERE username=? AND id<>?
                """, Integer.class, username, userId);
        return count != null && count > 0;
    }

    public boolean updateSupportUser(long userId, String username, String displayName, String status, int dailyQuota) {
        return jdbcTemplate.update("""
                UPDATE support_users
                   SET username=?, display_name=?, status=?, daily_quota=?, updated_at=CURRENT_TIMESTAMP(3)
                 WHERE id=? AND role_code='SUPPORT_AGENT'
                """, username, displayName, status, dailyQuota, userId) == 1;
    }

    public long supportUserReferenceCount(long userId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM customer_accounts WHERE user_id=?) +
                    (SELECT COUNT(*) FROM support_conversations WHERE assigned_user_id=?) +
                    (SELECT COUNT(*) FROM conversation_messages WHERE sender_user_id=?) +
                    (SELECT COUNT(*) FROM refund_requests WHERE requested_by=? OR reviewed_by=?) +
                    (SELECT COUNT(*) FROM portal_notifications WHERE recipient_user_id=?) +
                    (SELECT COUNT(*) FROM portal_audit_logs WHERE actor_user_id=?) +
                    (SELECT COUNT(*) FROM diagnosis_tasks WHERE requested_by=?)
                """, Long.class, userId, userId, userId, userId, userId, userId, userId, userId);
        return count == null ? 0 : count;
    }

    public boolean deleteSupportUser(long userId) {
        return jdbcTemplate.update("""
                DELETE FROM support_users
                 WHERE id=? AND role_code='SUPPORT_AGENT'
                """, userId) == 1;
    }

    public int releaseActiveConversations(long userId) {
        return jdbcTemplate.update("""
                UPDATE support_conversations
                   SET assigned_user_id=NULL,
                       service_mode='WAITING_AGENT',
                       updated_at=CURRENT_TIMESTAMP(3)
                 WHERE assigned_user_id=?
                   AND service_mode IN ('AGENT_SERVING', 'WAITING_CUSTOMER')
                """, userId);
    }

    public boolean disableSupportUser(long userId) {
        return jdbcTemplate.update("""
                UPDATE support_users
                   SET status='DISABLED', updated_at=CURRENT_TIMESTAMP(3)
                 WHERE id=? AND role_code='SUPPORT_AGENT'
                """, userId) == 1;
    }

    public boolean insertConversationReply(String conversationNo, long senderUserId, String content) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO conversation_messages
                  (conversation_id, sender_type, sender_user_id, content, sent_at)
                SELECT sc.id, 'SUPPORT_AGENT', ?, ?, CURRENT_TIMESTAMP(3)
                  FROM support_conversations sc
                 WHERE sc.conversation_no=? AND sc.assigned_user_id=?
                """, senderUserId, content, conversationNo, senderUserId);
        if (inserted == 1) {
            jdbcTemplate.update("""
                    UPDATE support_conversations
                       SET summary=?, last_message_at=CURRENT_TIMESTAMP(3), service_mode='AGENT_SERVING'
                     WHERE conversation_no=?
                    """, content, conversationNo);
        }
        return inserted == 1;
    }

    public ConversationContextRecord findAgentConversation(String conversationNo, long agentUserId) {
        List<ConversationContextRecord> rows = jdbcTemplate.query("""
                SELECT sc.id AS conversation_id, sc.conversation_no,
                       COALESCE(NULLIF(cu.display_name,''), '客户') AS customer_name,
                       t.ticket_no, COALESCE(t.business_no, o.order_no) AS order_no,
                       COALESCE(o.payable_amount, 0) AS order_amount,
                       GREATEST(COALESCE(o.payable_amount, 0) - COALESCE((
                           SELECT SUM(rr.requested_amount)
                             FROM refund_requests rr
                            WHERE rr.order_id=o.id
                              AND rr.status NOT IN ('REJECTED','CANCELLED')
                       ), 0), 0) AS refundable_amount,
                       COALESCE(p.channel, 'ORIGINAL') AS refund_channel,
                       sc.service_mode, sc.related_diagnosis_id,
                       CASE WHEN sc.related_diagnosis_id IS NOT NULL
                            THEN COALESCE(dt.scenario_type, t.scenario_hint)
                            ELSE NULL END AS scenario_hint,
                       CASE WHEN sc.related_diagnosis_id IS NOT NULL
                            THEN dr.customer_reply ELSE NULL END AS diagnosis_reply,
                       (SELECT rr.status
                          FROM refund_requests rr
                         WHERE rr.order_id=o.id
                         ORDER BY rr.created_at DESC, rr.id DESC
                         LIMIT 1) AS refund_status
                  FROM support_conversations sc
                  JOIN customers c ON c.id=sc.customer_id
                  LEFT JOIN customer_accounts ca ON ca.customer_id=c.id
                  LEFT JOIN support_users cu ON cu.id=ca.user_id
                  LEFT JOIN tickets t ON t.id=sc.related_ticket_id
                  LEFT JOIN diagnosis_tasks dt ON dt.id=sc.related_diagnosis_id
                  LEFT JOIN diagnosis_reports dr ON dr.diagnosis_id=sc.related_diagnosis_id
                  LEFT JOIN biz_orders o ON o.customer_id=sc.customer_id
                                        AND o.order_no=t.business_no
                  LEFT JOIN payment_records p ON p.order_id=o.id
                 WHERE sc.conversation_no=?
                   AND (sc.assigned_user_id=? OR
                        (sc.assigned_user_id IS NULL AND sc.service_mode='WAITING_AGENT'))
                 ORDER BY p.created_at DESC
                 LIMIT 1
                """, (rs, rowNum) -> new ConversationContextRecord(
                rs.getLong("conversation_id"), rs.getString("conversation_no"),
                rs.getString("customer_name"), rs.getString("ticket_no"), rs.getString("order_no"),
                rs.getBigDecimal("order_amount"), rs.getBigDecimal("refundable_amount"),
                rs.getString("refund_channel"), rs.getString("service_mode"),
                rs.getObject("related_diagnosis_id", Long.class),
                rs.getString("scenario_hint"), rs.getString("refund_status"),
                rs.getString("diagnosis_reply")),
                conversationNo, agentUserId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public CustomerConversationRecord findCustomerConversation(String conversationNo, long customerId) {
        List<CustomerConversationRecord> rows = jdbcTemplate.query("""
                SELECT sc.id, sc.conversation_no, sc.service_mode, u.display_name
                  FROM support_conversations sc
                  LEFT JOIN support_users u ON u.id=sc.assigned_user_id
                 WHERE sc.conversation_no=? AND sc.customer_id=?
                """, (rs, rowNum) -> new CustomerConversationRecord(
                rs.getLong("id"), rs.getString("conversation_no"),
                rs.getString("service_mode"), rs.getString("display_name")),
                conversationNo, customerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int claimConversation(long conversationId, long agentUserId) {
        return jdbcTemplate.update("""
                UPDATE support_conversations
                   SET assigned_user_id=?, service_mode='AGENT_SERVING',
                       updated_at=CURRENT_TIMESTAMP(3)
                 WHERE id=?
                   AND (assigned_user_id=? OR
                        (assigned_user_id IS NULL AND service_mode='WAITING_AGENT'))
                """, agentUserId, conversationId, agentUserId);
    }

    public List<ConversationMessageRecord> selectConversationMessages(long conversationId) {
        return jdbcTemplate.query("""
                SELECT id, sender_type, content, sent_at
                  FROM conversation_messages
                 WHERE conversation_id=?
                 ORDER BY sent_at, id
                """, (rs, rowNum) -> new ConversationMessageRecord(
                rs.getLong("id"), rs.getString("sender_type"), rs.getString("content"),
                time(rs, "sent_at")), conversationId);
    }

    public List<ConversationAttachmentRecord> selectConversationAttachments(long conversationId) {
        return jdbcTemplate.query("""
                SELECT a.id, a.message_id, a.original_name, a.content_type, a.size_bytes
                  FROM conversation_attachments a
                  JOIN conversation_messages m ON m.id=a.message_id
                 WHERE m.conversation_id=?
                 ORDER BY a.id
                """, (rs, rowNum) -> new ConversationAttachmentRecord(
                rs.getLong("id"), rs.getLong("message_id"), rs.getString("original_name"),
                rs.getString("content_type"), rs.getLong("size_bytes")), conversationId);
    }

    public long insertAgentMessage(long conversationId, long senderUserId, String content) {
        jdbcTemplate.update("""
                INSERT INTO conversation_messages
                  (conversation_id, sender_type, sender_user_id, content, sent_at)
                VALUES (?, 'SUPPORT_AGENT', ?, ?, CURRENT_TIMESTAMP(3))
                """, conversationId, senderUserId, content);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                UPDATE support_conversations
                   SET summary=?, last_message_at=CURRENT_TIMESTAMP(3),
                       service_mode='WAITING_CUSTOMER'
                 WHERE id=?
                """, content, conversationId);
        return id == null ? 0 : id;
    }

    public void insertConversationAttachment(long messageId, String fileName, String contentType,
                                             byte[] content) {
        jdbcTemplate.update("""
                INSERT INTO conversation_attachments
                  (message_id, original_name, content_type, size_bytes, content)
                VALUES (?, ?, ?, ?, ?)
                """, messageId, fileName, contentType, content.length, content);
    }

    public String findActiveCustomerConversation(long customerId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT conversation_no
                  FROM support_conversations
                 WHERE customer_id=?
                   AND service_mode IN ('WAITING_AGENT','AGENT_SERVING','WAITING_CUSTOMER')
                 ORDER BY last_message_at DESC LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), customerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String findAssignedAgentName(String conversationNo) {
        List<String> rows = jdbcTemplate.query("""
                SELECT u.display_name
                  FROM support_conversations sc
                  JOIN support_users u ON u.id=sc.assigned_user_id
                 WHERE sc.conversation_no=?
                """, (rs, rowNum) -> rs.getString(1), conversationNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AvailableAgentRecord findLeastLoadedAgent() {
        List<AvailableAgentRecord> rows = jdbcTemplate.query("""
                SELECT u.id, u.display_name
                  FROM support_users u
                  LEFT JOIN support_conversations sc
                    ON sc.assigned_user_id=u.id
                   AND sc.service_mode IN ('AGENT_SERVING','WAITING_CUSTOMER')
                 WHERE u.role_code='SUPPORT_AGENT' AND u.status='ACTIVE'
                 GROUP BY u.id, u.display_name, u.daily_quota
                HAVING COUNT(sc.id) < u.daily_quota
                 ORDER BY COUNT(sc.id), u.id
                 LIMIT 1
                """, (rs, rowNum) -> new AvailableAgentRecord(
                rs.getLong("id"), rs.getString("display_name")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createHandoffConversation(String conversationNo, long customerId, Long agentUserId,
                                          String ticketNo, String businessNo, Long diagnosisId,
                                          String summary) {
        jdbcTemplate.update("""
                INSERT INTO support_conversations
                  (conversation_no, customer_id, assigned_user_id, related_ticket_id, related_diagnosis_id,
                   service_mode, summary, risk_level, last_message_at)
                SELECT ?, ?, ?, t.id, ?, ?, ?, 'LOW', CURRENT_TIMESTAMP(3)
                  FROM (SELECT 1) seed
                  LEFT JOIN tickets t
                    ON t.customer_id=?
                   AND ((? IS NOT NULL AND t.ticket_no=?) OR
                        (? IS NOT NULL AND t.business_no=?))
                 ORDER BY t.updated_at DESC
                 LIMIT 1
                """, conversationNo, customerId, agentUserId, diagnosisId,
                agentUserId == null ? "WAITING_AGENT" : "AGENT_SERVING", summary,
                customerId, ticketNo, ticketNo, businessNo, businessNo);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    public Long findCustomerDiagnosis(long diagnosisId, long userId, long customerId) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT dt.id
                  FROM diagnosis_tasks dt
                  JOIN tickets t ON t.id=dt.ticket_id
                 WHERE dt.id=? AND dt.requested_by=? AND t.customer_id=?
                   AND dt.status IN ('SUCCESS','DEGRADED_SUCCESS')
                """, (rs, rowNum) -> rs.getLong(1), diagnosisId, userId, customerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long findConversationIdForCustomer(String conversationNo, long customerId) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT id FROM support_conversations
                 WHERE conversation_no=? AND customer_id=?
                """, (rs, rowNum) -> rs.getLong(1), conversationNo, customerId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    public long insertCustomerMessage(long conversationId, long senderUserId, String content) {
        jdbcTemplate.update("""
                INSERT INTO conversation_messages
                  (conversation_id, sender_type, sender_user_id, content, sent_at)
                VALUES (?, 'CUSTOMER', ?, ?, CURRENT_TIMESTAMP(3))
                """, conversationId, senderUserId, content);
        Long messageId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                UPDATE support_conversations
                   SET summary=?, last_message_at=CURRENT_TIMESTAMP(3),
                       service_mode=IF(assigned_user_id IS NULL, 'WAITING_AGENT', 'AGENT_SERVING'),
                       agent_archived_at=NULL
                 WHERE id=?
                """, content, conversationId);
        return messageId == null ? 0 : messageId;
    }

    public void updateCustomerConversationBusiness(long conversationId, long customerId,
                                                   String ticketNo, String businessNo) {
        jdbcTemplate.update("""
                UPDATE support_conversations sc
                LEFT JOIN tickets t ON t.customer_id=?
                  AND ((? IS NOT NULL AND t.ticket_no=?) OR
                       (? IS NOT NULL AND t.business_no=?))
                   SET sc.related_ticket_id=COALESCE(t.id, sc.related_ticket_id)
                 WHERE sc.id=? AND sc.customer_id=?
                """, customerId, ticketNo, ticketNo, businessNo, businessNo,
                conversationId, customerId);
    }

    public void updateCustomerConversationDiagnosis(long conversationId, long customerId,
                                                     long diagnosisId) {
        jdbcTemplate.update("""
                UPDATE support_conversations
                   SET related_diagnosis_id=?, updated_at=CURRENT_TIMESTAMP(3)
                 WHERE id=? AND customer_id=?
                """, diagnosisId, conversationId, customerId);
    }

    public void reopenCustomerConversation(long conversationId, long customerId) {
        jdbcTemplate.update("""
                UPDATE support_conversations
                   SET agent_archived_at=NULL,
                       service_mode=IF(assigned_user_id IS NULL, 'WAITING_AGENT', 'AGENT_SERVING'),
                       updated_at=CURRENT_TIMESTAMP(3)
                 WHERE id=? AND customer_id=?
                """, conversationId, customerId);
    }

    public boolean recallCustomerMessage(String conversationNo, long customerId,
                                         long senderUserId, long messageId) {
        return jdbcTemplate.update("""
                UPDATE conversation_messages m
                JOIN support_conversations sc ON sc.id=m.conversation_id
                   SET m.content=?, sc.summary=?
                 WHERE m.id=? AND sc.conversation_no=? AND sc.customer_id=?
                   AND m.sender_type='CUSTOMER' AND m.sender_user_id=?
                   AND m.content<>?
                   AND m.sent_at>=DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE)
                """, "该消息已撤回", "该消息已撤回", messageId, conversationNo,
                customerId, senderUserId, "该消息已撤回") > 0;
    }

    public int archiveConversation(String conversationNo, long agentUserId) {
        return jdbcTemplate.update("""
                UPDATE support_conversations
                   SET agent_archived_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                 WHERE conversation_no=? AND agent_archived_at IS NULL
                   AND (assigned_user_id=? OR service_mode='WAITING_AGENT')
                """, conversationNo, agentUserId);
    }

    public int archiveCompletedConversations(long agentUserId) {
        return jdbcTemplate.update("""
                UPDATE support_conversations
                   SET agent_archived_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                 WHERE assigned_user_id=? AND agent_archived_at IS NULL
                   AND service_mode IN ('WAITING_CUSTOMER','ENDED')
                """, agentUserId);
    }

    public long countProductKnowledgeForOrder(String orderNo) {
        return count("""
                SELECT COUNT(*)
                  FROM product_knowledge_documents d
                 WHERE d.product_sku=(
                       SELECT SUBSTRING_INDEX(TRIM(o.product_scope), ' ', 1)
                         FROM biz_orders o WHERE o.order_no=?
                 )
                """, orderNo);
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private static String like(String keyword) {
        return "%" + keyword + "%";
    }

    private static ItemRecord item(ResultSet rs, int rowNum) throws SQLException {
        return new ItemRecord(rs.getString("item_id"), rs.getString("title"), rs.getString("detail"),
                rs.getString("meta"), rs.getString("status"), time(rs, "occurred_at"));
    }

    private static RefundRecord refund(ResultSet rs, int rowNum) throws SQLException {
        return new RefundRecord(rs.getLong("id"), rs.getString("refund_no"), rs.getString("customer_name"),
                rs.getString("order_no"), rs.getString("product_scope"), rs.getBigDecimal("order_amount"),
                rs.getBigDecimal("requested_amount"), rs.getBigDecimal("approved_amount"),
                rs.getString("status"), rs.getString("risk_level"), rs.getString("risk_message"),
                rs.getString("refund_channel"), rs.getString("reject_reason"), time(rs, "requested_at"),
                time(rs, "reviewed_at"), time(rs, "expected_arrival_at"), time(rs, "completed_at"));
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }
}
