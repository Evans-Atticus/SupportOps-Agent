SET NAMES utf8mb4;
USE supportops;

-- customer02 的客户侧演示数据；所有语句均可重复执行。
INSERT INTO biz_orders
    (order_no, customer_id, order_status, payment_status, total_amount, payable_amount,
     currency, product_scope, paid_at, cancelled_at, created_at, updated_at)
SELECT 'O202607280201', ca.customer_id, 'PENDING_PAYMENT', 'UNPAID', 299.00, 299.00,
       'CNY', 'SKU-C201 智能耳机 Lite', NULL, NULL,
       '2026-07-28 09:20:00.000', '2026-07-28 09:21:00.000'
  FROM customer_accounts ca JOIN support_users u ON u.id=ca.user_id
 WHERE u.username='customer02'
ON DUPLICATE KEY UPDATE product_scope=VALUES(product_scope), updated_at=VALUES(updated_at);

INSERT INTO biz_orders
    (order_no, customer_id, order_status, payment_status, total_amount, payable_amount,
     currency, product_scope, paid_at, cancelled_at, created_at, updated_at)
SELECT 'O202607280202', ca.customer_id, 'SHIPPED', 'PAID', 599.00, 559.00,
       'CNY', 'SKU-C202 智能手表 S2', '2026-07-27 14:31:00.000', NULL,
       '2026-07-27 14:30:00.000', '2026-07-28 11:10:00.000'
  FROM customer_accounts ca JOIN support_users u ON u.id=ca.user_id
 WHERE u.username='customer02'
ON DUPLICATE KEY UPDATE product_scope=VALUES(product_scope), updated_at=VALUES(updated_at);

INSERT INTO biz_orders
    (order_no, customer_id, order_status, payment_status, total_amount, payable_amount,
     currency, product_scope, paid_at, cancelled_at, created_at, updated_at)
SELECT 'O202607280203', ca.customer_id, 'CANCELLED', 'UNPAID', 128.00, 128.00,
       'CNY', 'SKU-C203 便携充电宝', NULL, '2026-07-26 10:30:00.000',
       '2026-07-26 10:00:00.000', '2026-07-28 10:00:00.000'
  FROM customer_accounts ca JOIN support_users u ON u.id=ca.user_id
 WHERE u.username='customer02'
ON DUPLICATE KEY UPDATE order_status=VALUES(order_status), payment_status=VALUES(payment_status),
                        product_scope=VALUES(product_scope), paid_at=VALUES(paid_at),
                        cancelled_at=VALUES(cancelled_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
    (ticket_no, customer_id, business_no, channel, description, scenario_hint, status, priority, created_at)
SELECT 'TK-C02-20260728-001', ca.customer_id, 'O202607280201', 'WEB',
       '银行卡已经扣款，但订单仍显示待支付。', 'PAYMENT_SUCCESS_ORDER_PENDING', 'OPEN', 'HIGH',
       '2026-07-28 09:25:00.000'
  FROM customer_accounts ca JOIN support_users u ON u.id=ca.user_id WHERE u.username='customer02'
ON DUPLICATE KEY UPDATE description=VALUES(description), scenario_hint=VALUES(scenario_hint);

INSERT INTO tickets
    (ticket_no, customer_id, business_no, channel, description, scenario_hint, status, priority, created_at)
SELECT 'TK-C02-20260728-002', ca.customer_id, 'O202607280202', 'WEB',
       '想查询智能手表订单的最新物流进度。', 'LOGISTICS_STATUS_NOT_SYNCED', 'PROCESSING', 'NORMAL',
       '2026-07-28 11:15:00.000'
  FROM customer_accounts ca JOIN support_users u ON u.id=ca.user_id WHERE u.username='customer02'
ON DUPLICATE KEY UPDATE description=VALUES(description), scenario_hint=VALUES(scenario_hint);

INSERT INTO tickets
    (ticket_no, customer_id, business_no, channel, description, scenario_hint, status, priority, created_at)
SELECT 'TK-C02-20260728-003', ca.customer_id, 'O202607280203', 'APP',
       '订单已取消，想确认是否还会安排发货。', 'LOGISTICS_TRACKING_QUERY', 'OPEN', 'NORMAL',
       '2026-07-28 10:05:00.000'
  FROM customer_accounts ca JOIN support_users u ON u.id=ca.user_id WHERE u.username='customer02'
ON DUPLICATE KEY UPDATE description=VALUES(description), scenario_hint=VALUES(scenario_hint);

-- O202607280203 是未完成购买的取消订单；清理旧版退款演示用例，避免状态相互矛盾。
DELETE FROM portal_notifications WHERE related_no='RF-C02-20260728-003';
DELETE r FROM refund_records r
  JOIN biz_orders o ON o.id=r.order_id
 WHERE o.order_no='O202607280203' AND r.refund_no='R-C02-20260728-003';
DELETE r FROM refund_requests r
  JOIN biz_orders o ON o.id=r.order_id
 WHERE o.order_no='O202607280203' AND r.refund_no='RF-C02-20260728-003';
DELETE p FROM payment_records p
  JOIN biz_orders o ON o.id=p.order_id
 WHERE o.order_no='O202607280203' AND p.payment_no='P-C02-20260728-003';

INSERT INTO payment_records
    (payment_no, order_id, channel, payment_status, amount, callback_status,
     callback_error_code, callback_attempts, paid_at, callback_at, created_at)
SELECT 'P-C02-20260728-001', o.id, 'ALIPAY', 'SUCCESS', 299.00, 'FAILED',
       'ORDER_SERVICE_TIMEOUT', 3, '2026-07-28 09:21:00.000', '2026-07-28 09:24:00.000',
       '2026-07-28 09:21:00.000'
  FROM biz_orders o WHERE o.order_no='O202607280201'
ON DUPLICATE KEY UPDATE callback_status=VALUES(callback_status), callback_error_code=VALUES(callback_error_code);

INSERT INTO payment_records
    (payment_no, order_id, channel, payment_status, amount, callback_status,
     callback_attempts, paid_at, callback_at, created_at)
SELECT 'P-C02-20260728-002', o.id, 'WECHAT', 'SUCCESS', 559.00, 'SUCCESS',
       1, '2026-07-27 14:31:00.000', '2026-07-27 14:31:02.000', '2026-07-27 14:31:00.000'
  FROM biz_orders o WHERE o.order_no='O202607280202'
ON DUPLICATE KEY UPDATE payment_status=VALUES(payment_status), callback_status=VALUES(callback_status);

INSERT INTO logistics_records
    (tracking_no, order_id, source_type, carrier_name, logistics_status, status_description,
     origin_location, destination_location, current_location, facility_name, estimated_delivery_at,
     event_time, synced_at, created_at)
SELECT 'SF202607280202', o.id, 'LOCAL', '顺丰速运', 'IN_TRANSIT', '包裹已从上海浦东分拨中心发出',
       '上海市浦东新区', '江苏省苏州市工业园区', '上海市浦东新区', '上海浦东分拨中心',
       '2026-07-28 18:00:00.000', '2026-07-27 20:05:00.000', '2026-07-27 20:06:00.000', '2026-07-27 20:06:00.000'
  FROM biz_orders o WHERE o.order_no='O202607280202'
  AND NOT EXISTS (SELECT 1 FROM logistics_records l WHERE l.tracking_no='SF202607280202' AND l.source_type='LOCAL');

INSERT INTO logistics_records
    (tracking_no, order_id, source_type, carrier_name, logistics_status, status_description,
     origin_location, destination_location, current_location, facility_name,
     courier_name_masked, courier_phone_masked, estimated_delivery_at, event_time, synced_at, created_at)
SELECT 'SF202607280202', o.id, 'CARRIER', '顺丰速运', 'OUT_FOR_DELIVERY',
       '快递员正在苏州市工业园区金鸡湖街道派送，请保持电话畅通',
       '上海市浦东新区', '江苏省苏州市工业园区', '江苏省苏州市工业园区金鸡湖街道',
       '顺丰苏州工业园区营业点', '李师傅', '138****6208', '2026-07-28 18:00:00.000',
       '2026-07-28 13:20:00.000', NULL, '2026-07-28 13:20:05.000'
  FROM biz_orders o WHERE o.order_no='O202607280202'
  AND NOT EXISTS (SELECT 1 FROM logistics_records l WHERE l.tracking_no='SF202607280202' AND l.source_type='CARRIER');

INSERT INTO portal_notifications
    (recipient_user_id, notification_type, title, content, related_type, related_no, read_flag, created_at)
SELECT u.id, 'LOGISTICS_STATUS', '包裹正在派送',
       '订单 O202607280202 的包裹已进入派送阶段，请保持电话畅通。',
       'ORDER', 'O202607280202', 0, '2026-07-28 13:21:00.000'
  FROM support_users u WHERE u.username='customer02'
  AND NOT EXISTS (SELECT 1 FROM portal_notifications n WHERE n.recipient_user_id=u.id AND n.related_no='O202607280202');
