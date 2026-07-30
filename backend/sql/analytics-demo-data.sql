SET NAMES utf8mb4;
USE supportops;

-- 本文件仅用于本地演示环境。每条记录均复用 data.sql 中已有客户、订单和已定义的
-- 业务场景，不生成脱离业务实体的汇总数；页面上的统计值全部由这些明细实时聚合。
-- DEMO-TK 前缀用于明确区分演示工单与外部系统同步的正式工单。

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0718-001', c.id, 'O202607060005', 'WEB',
       '客户反馈物流长时间未更新，客服已联系承运商核实节点。',
       'LOGISTICS_STATUS_NOT_SYNCED', 'RESOLVED', 'NORMAL',
       '2026-07-18 09:12:00.000', '2026-07-18 15:40:00.000'
  FROM customers c WHERE c.customer_no='C202607005'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0718-002', c.id, 'O202607060001', 'APP',
       '支付成功后订单状态延迟，重新同步支付回调后恢复。',
       'PAYMENT_SUCCESS_ORDER_PENDING', 'CLOSED', 'HIGH',
       '2026-07-18 13:25:00.000', '2026-07-18 17:18:00.000'
  FROM customers c WHERE c.customer_no='C202607001'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0719-001', c.id, 'O202607060003', 'WEB',
       '客户咨询优惠券适用门槛，客服已说明订单金额规则。',
       'COUPON_UNAVAILABLE', 'RESOLVED', 'LOW',
       '2026-07-19 10:06:00.000', '2026-07-19 10:42:00.000'
  FROM customers c WHERE c.customer_no='C202607003'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0720-001', c.id, 'CLIENT-DEMO-01', 'API',
       '开放平台查询接口间歇性超时，技术支持正在核对网关日志。',
       'API_FREQUENT_FAILURE', 'PROCESSING', 'URGENT',
       '2026-07-20 08:45:00.000', '2026-07-24 09:20:00.000'
  FROM customers c WHERE c.customer_no='C202607006'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0720-002', c.id, 'O202607060007', 'WEB',
       '企业发票开具失败，重新提交税务信息后已完成。',
       'INVOICE_ISSUE_FAILED', 'CLOSED', 'NORMAL',
       '2026-07-20 14:30:00.000', '2026-07-21 11:05:00.000'
  FROM customers c WHERE c.customer_no='C202607007'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0721-001', c.id, 'O202607060002', 'APP',
       '客户取消订单后询问退款到账时间，客服已提交退款审批。',
       'ORDER_CANCELLED_BUT_CHARGED', 'PROCESSING', 'HIGH',
       '2026-07-21 09:18:00.000', '2026-07-23 14:51:30.000'
  FROM customers c WHERE c.customer_no='C202607002'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0721-002', c.id, 'M202607060004', 'WEB',
       '会员权益补发申请已受理，权益服务完成补发。',
       'MEMBER_BENEFIT_NOT_RECEIVED', 'RESOLVED', 'NORMAL',
       '2026-07-21 16:22:00.000', '2026-07-22 09:35:00.000'
  FROM customers c WHERE c.customer_no='C202607004'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0722-001', c.id, 'O202607060005', 'APP',
       '签收状态与平台不一致，正在等待物流系统下一次同步。',
       'LOGISTICS_STATUS_NOT_SYNCED', 'PROCESSING', 'HIGH',
       '2026-07-22 11:14:00.000', '2026-07-24 08:50:00.000'
  FROM customers c WHERE c.customer_no='C202607005'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0722-002', c.id, 'O202607060001', 'WEB',
       '订单支付状态回写延迟，等待订单服务确认。',
       'PAYMENT_SUCCESS_ORDER_PENDING', 'OPEN', 'URGENT',
       '2026-07-22 18:08:00.000', '2026-07-24 10:05:00.000'
  FROM customers c WHERE c.customer_no='C202607001'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0723-001', c.id, 'O202607060003', 'APP',
       '优惠券使用范围咨询，已转知识库答复。',
       'COUPON_UNAVAILABLE', 'RESOLVED', 'LOW',
       '2026-07-23 09:40:00.000', '2026-07-23 10:12:00.000'
  FROM customers c WHERE c.customer_no='C202607003'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0723-002', c.id, 'O202607060007', 'WEB',
       '客户补充发票抬头后重新进入开票流程。',
       'INVOICE_ISSUE_FAILED', 'PROCESSING', 'NORMAL',
       '2026-07-23 15:26:00.000', '2026-07-24 09:55:00.000'
  FROM customers c WHERE c.customer_no='C202607007'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0724-001', c.id, 'O202607060002', 'APP',
       '退款申请已进入人工风险复核，等待管理员审批。',
       'ORDER_CANCELLED_BUT_CHARGED', 'OPEN', 'HIGH',
       '2026-07-24 08:36:00.000', '2026-07-24 08:36:00.000'
  FROM customers c WHERE c.customer_no='C202607002'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0724-002', c.id, 'O202607060005', 'WEB',
       '客户反馈包裹签收照片与收货地址不一致，等待物流核查。',
       'LOGISTICS_STATUS_NOT_SYNCED', 'OPEN', 'URGENT',
       '2026-07-24 09:48:00.000', '2026-07-24 09:48:00.000'
  FROM customers c WHERE c.customer_no='C202607005'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);

INSERT INTO tickets
  (ticket_no, customer_id, business_no, channel, description, scenario_hint,
   status, priority, created_at, updated_at)
SELECT 'DEMO-TK-0724-003', c.id, 'CLIENT-DEMO-01', 'API',
       '开放平台调用方反馈订单接口响应变慢，已进入排查队列。',
       'API_FREQUENT_FAILURE', 'OPEN', 'NORMAL',
       '2026-07-24 10:20:00.000', '2026-07-24 10:20:00.000'
  FROM customers c WHERE c.customer_no='C202607006'
ON DUPLICATE KEY UPDATE status=VALUES(status), priority=VALUES(priority),
  channel=VALUES(channel), description=VALUES(description), scenario_hint=VALUES(scenario_hint),
  created_at=VALUES(created_at), updated_at=VALUES(updated_at);
