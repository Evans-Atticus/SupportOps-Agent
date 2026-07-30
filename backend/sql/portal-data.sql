SET NAMES utf8mb4;
USE supportops;

-- 为订单、工单和物流搜索提供可见且可检索的 SKU / 产品名称。
UPDATE biz_orders
   SET product_scope = CASE order_no
       WHEN 'O202607060001' THEN 'SKU-A018 智能耳机 Pro'
       WHEN 'O202607060002' THEN 'SKU-B026 便携显示器'
       WHEN 'O202607060003' THEN 'SKU-C031 智能优惠套餐'
       WHEN 'O202607060005' THEN 'SKU-D045 智能手表'
       WHEN 'O202607060007' THEN 'SKU-S088 企业发票服务'
       ELSE product_scope
   END;

INSERT INTO customer_accounts (user_id, customer_id)
SELECT u.id, 1 FROM support_users u WHERE u.username = 'customer01'
ON DUPLICATE KEY UPDATE customer_id=VALUES(customer_id);

INSERT INTO support_conversations
  (conversation_no, customer_id, assigned_user_id, related_ticket_id, service_mode,
   summary, risk_level, last_message_at)
SELECT 'CONV-20260723-001', 1, u.id, 1, 'AGENT_SERVING',
       '客户咨询支付状态与退款到账时间，智能体已完成订单和支付信息核验。', 'LOW',
       '2026-07-23 10:24:00.000'
  FROM support_users u WHERE u.username='support01'
ON DUPLICATE KEY UPDATE assigned_user_id=VALUES(assigned_user_id), summary=VALUES(summary);

INSERT INTO support_conversations
  (conversation_no, customer_id, assigned_user_id, related_ticket_id, service_mode,
   summary, risk_level, last_message_at)
SELECT 'CONV-20260724-002', t.customer_id, u.id, t.id, 'AGENT_SERVING',
       '客户反馈物流显示已签收但本人未收到，需要客服核验签收凭证与配送节点。', 'MEDIUM',
       '2026-07-24 09:48:00.000'
  FROM tickets t JOIN support_users u ON u.username='support01'
 WHERE t.ticket_no='TK-0706-005'
ON DUPLICATE KEY UPDATE assigned_user_id=VALUES(assigned_user_id), summary=VALUES(summary);

INSERT INTO support_conversations
  (conversation_no, customer_id, assigned_user_id, related_ticket_id, service_mode,
   summary, risk_level, last_message_at)
SELECT 'CONV-20260724-003', t.customer_id, u.id, t.id, 'AGENT_SERVING',
       '客户咨询订单取消后的退款进度，需要客服结合支付渠道状态持续回复。', 'HIGH',
       '2026-07-24 10:06:00.000'
  FROM tickets t JOIN support_users u ON u.username='support01'
 WHERE t.ticket_no='TK-0706-002'
ON DUPLICATE KEY UPDATE assigned_user_id=VALUES(assigned_user_id), summary=VALUES(summary);

INSERT INTO conversation_messages
  (conversation_id, sender_type, sender_user_id, content, sent_at)
SELECT c.id, 'CUSTOMER', u.id, '退款已经通过了，请问什么时候能到账？',
       '2026-07-23 10:24:00.000'
  FROM support_conversations c JOIN support_users u ON u.username='customer01'
 WHERE c.conversation_no='CONV-20260723-001'
   AND NOT EXISTS (SELECT 1 FROM conversation_messages m WHERE m.conversation_id=c.id);

INSERT INTO conversation_messages
  (conversation_id, sender_type, content, sent_at)
SELECT c.id, 'BOT',
       '退款审批完成后将由支付渠道原路退回，客服可以继续查看执行和到账状态。',
       '2026-07-23 10:24:05.000'
  FROM support_conversations c
 WHERE c.conversation_no='CONV-20260723-001'
   AND (SELECT COUNT(*) FROM conversation_messages m WHERE m.conversation_id=c.id) = 1;

INSERT INTO conversation_messages
  (conversation_id, sender_type, content, sent_at)
SELECT c.id, 'CUSTOMER', '物流显示已经签收，但我没有收到包裹，可以帮我查一下吗？',
       '2026-07-24 09:48:00.000'
  FROM support_conversations c
 WHERE c.conversation_no='CONV-20260724-002'
   AND NOT EXISTS (SELECT 1 FROM conversation_messages m WHERE m.conversation_id=c.id);

INSERT INTO conversation_messages
  (conversation_id, sender_type, content, sent_at)
SELECT c.id, 'CUSTOMER', '订单取消后退款一直没有到账，请帮我确认处理进度。',
       '2026-07-24 10:06:00.000'
  FROM support_conversations c
 WHERE c.conversation_no='CONV-20260724-003'
   AND NOT EXISTS (SELECT 1 FROM conversation_messages m WHERE m.conversation_id=c.id);

-- 修复早期通过不支持 UTF-8 的终端导入时可能落成问号的演示消息。
UPDATE conversation_messages m
JOIN support_conversations c ON c.id=m.conversation_id
SET m.content=CASE c.conversation_no
    WHEN 'CONV-20260724-002' THEN '物流显示已经签收，但我没有收到包裹，可以帮我查一下吗？'
    WHEN 'CONV-20260724-003' THEN '订单取消后退款一直没有到账，请帮我确认处理进度。'
    ELSE m.content
END
WHERE c.conversation_no IN ('CONV-20260724-002','CONV-20260724-003')
  AND m.sender_type='CUSTOMER';

UPDATE conversation_messages m
JOIN support_conversations c ON c.id=m.conversation_id
SET m.content='退款审批完成后将由支付渠道原路退回，客服可以继续查看执行和到账状态。'
WHERE c.conversation_no='CONV-20260723-001' AND m.sender_type='BOT';

UPDATE conversation_messages m
JOIN support_conversations c ON c.id=m.conversation_id
SET m.content='您好，退款正在由支付渠道处理中，预计 1—3 个工作日原路到账。'
WHERE c.conversation_no='CONV-20260723-001' AND m.sender_type='SUPPORT_AGENT'
  AND m.content REGEXP '^\\\\?+$|^[?：:]+$';

UPDATE conversation_messages
SET content='退款已经通过了，请问什么时候能到账？'
WHERE id=(
  SELECT message_id FROM (
    SELECT MIN(m.id) AS message_id
    FROM conversation_messages m
    JOIN support_conversations c ON c.id=m.conversation_id
    WHERE c.conversation_no='CONV-20260723-001' AND m.sender_type='CUSTOMER'
  ) first_customer
);

UPDATE conversation_messages
SET content='转人工客服'
WHERE id=(
  SELECT message_id FROM (
    SELECT MAX(m.id) AS message_id
    FROM conversation_messages m
    JOIN support_conversations c ON c.id=m.conversation_id
    WHERE c.conversation_no='CONV-20260723-001' AND m.sender_type='CUSTOMER'
    HAVING COUNT(*) > 1
  ) latest_customer
);

INSERT INTO refund_requests
  (refund_no, order_id, ticket_id, requested_by, requested_amount, approved_amount,
   status, reason, risk_level, risk_message, refund_channel, reviewed_by, reviewed_at,
   executed_at, expected_arrival_at)
SELECT 'RF-20260723-018', 1, 1, customer_user.id, 299.00, 299.00,
       'EXECUTING', '支付状态异常，客户申请原路退款', 'LOW',
       '订单、支付和工单信息一致，未发现重复退款。', 'ALIPAY',
       admin_user.id, '2026-07-23 09:48:00.000', '2026-07-23 09:50:00.000',
       '2026-07-26 09:50:00.000'
  FROM support_users customer_user JOIN support_users admin_user
    ON customer_user.username='customer01' AND admin_user.username='admin'
ON DUPLICATE KEY UPDATE
  status=VALUES(status),
  approved_amount=VALUES(approved_amount),
  reason=VALUES(reason),
  risk_level=VALUES(risk_level),
  risk_message=VALUES(risk_message),
  refund_channel=VALUES(refund_channel);

INSERT INTO refund_requests
  (refund_no, order_id, ticket_id, requested_by, requested_amount, status, reason,
   risk_level, risk_message, refund_channel)
SELECT 'RF-20260723-021', 2, 2, u.id, 128.00, 'UNDER_REVIEW',
       '订单取消后仍然扣款', 'HIGH', '订单全额退款且存在紧急工单，需要核验支付与历史售后。',
       'WECHAT'
  FROM support_users u WHERE u.username='support01'
ON DUPLICATE KEY UPDATE
  status=VALUES(status),
  reason=VALUES(reason),
  risk_level=VALUES(risk_level),
  risk_message=VALUES(risk_message),
  refund_channel=VALUES(refund_channel);

INSERT INTO portal_notifications
  (recipient_user_id, notification_type, title, content, related_type, related_no, read_flag)
SELECT u.id, 'REFUND_STATUS', '退款正在处理中',
       '退款 RF-20260723-018 已通过审批，预计 1—3 个工作日原路到账。',
       'REFUND', 'RF-20260723-018', 0
  FROM support_users u WHERE u.username='customer01'
  AND NOT EXISTS (
    SELECT 1 FROM portal_notifications n
     WHERE n.recipient_user_id=u.id AND n.related_no='RF-20260723-018'
  );

INSERT INTO portal_audit_logs
  (actor_user_id, action_code, resource_type, resource_no, result, detail, request_id, created_at)
SELECT u.id, 'REFUND_APPROVE', 'REFUND', 'RF-20260723-018', 'SUCCESS',
       '批准金额 299.00，退款渠道 ALIPAY。', 'seed-portal-audit',
       '2026-07-23 09:48:00.000'
  FROM support_users u WHERE u.username='admin'
  AND NOT EXISTS (
    SELECT 1 FROM portal_audit_logs a
     WHERE a.resource_no='RF-20260723-018' AND a.action_code='REFUND_APPROVE'
  );
