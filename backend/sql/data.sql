SET NAMES utf8mb4;
USE supportops;

-- Local role accounts. BCrypt value is for "12345678l" and must be replaced
-- before any non-local deployment.
INSERT INTO support_users
    (id, username, password_hash, display_name, role_code, status, daily_quota)
VALUES
    (1, 'admin', '$2a$10$3O/knFnjWYW.4ve6VouQ2ewGWZS1TDCBqabnCGPTVK4a0jr/t7bni', '系统管理员', 'ADMIN', 'ACTIVE', 2147483647),
    (2, 'support01', '$2a$10$3O/knFnjWYW.4ve6VouQ2ewGWZS1TDCBqabnCGPTVK4a0jr/t7bni', '客服小李', 'SUPPORT_AGENT', 'ACTIVE', 50),
    (3, 'customer01', '$2a$10$3O/knFnjWYW.4ve6VouQ2ewGWZS1TDCBqabnCGPTVK4a0jr/t7bni', '演示客户', 'CUSTOMER', 'ACTIVE', 10);

INSERT INTO customers
    (id, customer_no, customer_name, mobile_masked, email_masked, status)
VALUES
    (1, 'C202607001', '支付场景客户', '138****0001', 'pay***@example.com', 'ACTIVE'),
    (2, 'C202607002', '取消扣款客户', '138****0002', 'cancel***@example.com', 'ACTIVE'),
    (3, 'C202607003', '优惠券场景客户', '138****0003', 'coupon***@example.com', 'ACTIVE'),
    (4, 'C202607004', '会员场景客户', '138****0004', 'member***@example.com', 'ACTIVE'),
    (5, 'C202607005', '物流场景客户', '138****0005', 'ship***@example.com', 'ACTIVE'),
    (6, 'C202607006', '开放平台客户', '138****0006', 'api***@example.com', 'ACTIVE'),
    (7, 'C202607007', '发票场景客户', '138****0007', 'invoice***@example.com', 'ACTIVE');

INSERT INTO biz_orders
    (id, order_no, customer_id, order_status, payment_status, total_amount, payable_amount, currency,
     coupon_code, product_scope, paid_at, cancelled_at, created_at, updated_at)
VALUES
    (1, 'O202607060001', 1, 'PENDING_PAYMENT', 'UNPAID', 299.00, 299.00, 'CNY', NULL, 'SKU-A018 智能耳机 Pro',
     NULL, NULL, '2026-07-06 09:30:00.000', '2026-07-06 09:30:00.000'),
    (2, 'O202607060002', 2, 'CANCELLED', 'PAID', 128.00, 128.00, 'CNY', NULL, 'SKU-B026 便携显示器',
     '2026-07-06 10:00:04.000', '2026-07-06 10:01:00.000', '2026-07-06 09:58:00.000', '2026-07-06 10:01:00.000'),
    (3, 'O202607060003', 3, 'PENDING_PAYMENT', 'UNPAID', 199.00, 199.00, 'CNY', 'FULL300LESS50', 'SKU-C031 智能优惠套餐',
     NULL, NULL, '2026-07-06 10:30:00.000', '2026-07-06 10:30:00.000'),
    (5, 'O202607060005', 5, 'SHIPPED', 'PAID', 368.00, 368.00, 'CNY', NULL, 'SKU-D045 智能手表',
     '2026-07-05 08:00:00.000', NULL, '2026-07-05 07:55:00.000', '2026-07-06 08:00:00.000'),
    (7, 'O202607060007', 7, 'COMPLETED', 'PAID', 888.00, 888.00, 'CNY', NULL, 'SKU-S088 企业发票服务',
     '2026-07-05 14:00:00.000', NULL, '2026-07-05 13:58:00.000', '2026-07-06 12:00:00.000');

INSERT INTO tickets
    (id, ticket_no, customer_id, business_no, channel, description, scenario_hint, status, priority, created_at)
VALUES
    (1, 'TK-0706-001', 1, 'O202607060001', 'WEB',
     '客户已支付成功，但订单仍显示待支付，请诊断原因并生成客服回复。',
     'PAYMENT_SUCCESS_ORDER_PENDING', 'OPEN', 'HIGH', '2026-07-06 09:35:00.000'),
    (2, 'TK-0706-002', 2, 'O202607060002', 'APP',
     '订单已经取消，银行卡仍然扣款，也没有收到退款。',
     'ORDER_CANCELLED_BUT_CHARGED', 'OPEN', 'URGENT', '2026-07-06 10:10:00.000'),
    (3, 'TK-0706-003', 3, 'O202607060003', 'WEB',
     '优惠券显示可用，但结算时提示不满足使用条件。',
     'COUPON_UNAVAILABLE', 'OPEN', 'NORMAL', '2026-07-06 10:35:00.000'),
    (4, 'TK-0706-004', 4, 'M202607060004', 'APP',
     '会员已生效，承诺的每月运费券一直没有到账。',
     'MEMBER_BENEFIT_NOT_RECEIVED', 'OPEN', 'HIGH', '2026-07-06 11:00:00.000'),
    (5, 'TK-0706-005', 5, 'O202607060005', 'WEB',
     '快递公司已经显示签收，平台物流仍显示运输中。',
     'LOGISTICS_STATUS_NOT_SYNCED', 'OPEN', 'NORMAL', '2026-07-06 11:30:00.000'),
    (6, 'TK-0706-006', 6, 'CLIENT-DEMO-01', 'API',
     '开放平台订单查询接口近十分钟频繁返回 503。',
     'API_FREQUENT_FAILURE', 'OPEN', 'HIGH', '2026-07-06 12:00:00.000'),
    (7, 'TK-0706-007', 7, 'O202607060007', 'WEB',
     '企业发票提交后一直开具失败，请核查原因。',
     'INVOICE_ISSUE_FAILED', 'OPEN', 'NORMAL', '2026-07-06 12:30:00.000');

-- Scenario 1: payment is successful (normal fact), callback failed and order
-- remains unpaid (abnormal facts).
INSERT INTO payment_records
    (id, payment_no, order_id, channel, payment_status, amount, callback_status,
     callback_error_code, callback_attempts, paid_at, callback_at, created_at)
VALUES
    (1, 'P202607060001', 1, 'ALIPAY', 'SUCCESS', 299.00, 'FAILED',
     'ORDER_SERVICE_TIMEOUT', 3, '2026-07-06 09:31:12.000', '2026-07-06 09:34:30.000', '2026-07-06 09:31:10.000'),
    (2, 'P202607060002', 2, 'WECHAT', 'SUCCESS', 128.00, 'SUCCESS',
     NULL, 1, '2026-07-06 10:00:04.000', '2026-07-06 10:00:05.000', '2026-07-06 10:00:00.000');

-- Scenario 2: cancellation is complete and callback is normal, while the
-- refund remains pending beyond the expected window.
INSERT INTO refund_records
    (id, refund_no, order_id, payment_id, refund_status, refund_amount, failure_code,
     requested_at, completed_at, created_at)
VALUES
    (1, 'R202607060002', 2, 2, 'PENDING', 128.00, 'REFUND_JOB_NOT_TRIGGERED',
     '2026-07-06 10:01:02.000', NULL, '2026-07-06 10:01:02.000');

-- Scenario 3: coupon ownership, status, validity and scope are normal; the
-- order amount is below the configured threshold.
INSERT INTO coupons
    (id, coupon_code, coupon_name, coupon_status, threshold_amount, discount_amount,
     product_scope, valid_from, valid_until)
VALUES
    (1, 'FULL300LESS50', '满 300 减 50', 'ACTIVE', 300.00, 50.00,
     'ALL', '2026-07-01 00:00:00.000', '2026-07-31 23:59:59.999');

INSERT INTO customer_coupons
    (id, customer_id, coupon_id, receive_status, received_at)
VALUES
    (1, 3, 1, 'AVAILABLE', '2026-07-01 08:00:00.000');

-- Scenario 4: membership is active; one benefit is granted normally and the
-- requested shipping coupon failed to be granted.
INSERT INTO member_accounts
    (id, member_no, customer_id, member_level, member_status, valid_from, valid_until)
VALUES
    (1, 'M202607060004', 4, 'GOLD', 'ACTIVE', '2026-07-01 00:00:00.000', '2027-06-30 23:59:59.999');

INSERT INTO member_benefit_records
    (id, benefit_no, member_id, benefit_code, benefit_name, grant_status, failure_code,
     expected_at, granted_at, created_at)
VALUES
    (1, 'B202607040001', 1, 'BIRTHDAY_POINTS', '生日积分', 'GRANTED', NULL,
     '2026-07-01 00:05:00.000', '2026-07-01 00:03:00.000', '2026-07-01 00:00:00.000'),
    (2, 'B202607040002', 1, 'MONTHLY_SHIPPING_COUPON', '每月运费券', 'FAILED', 'GRANT_EVENT_LOST',
     '2026-07-01 00:05:00.000', NULL, '2026-07-01 00:00:00.000');

-- Scenario 5: carrier data is newer and delivered; local data is stale.
INSERT INTO logistics_records
    (id, tracking_no, order_id, source_type, logistics_status, status_description,
     event_time, synced_at, created_at)
VALUES
    (1, 'SF202607060005', 5, 'LOCAL', 'IN_TRANSIT', '快件运输中',
     '2026-07-06 08:00:00.000', '2026-07-06 08:00:10.000', '2026-07-06 08:00:10.000'),
    (2, 'SF202607060005', 5, 'CARRIER', 'DELIVERED', '本人签收',
     '2026-07-06 10:20:00.000', NULL, '2026-07-06 10:20:05.000');

-- Scenario 6: six calls in ten minutes, five failures and one normal success.
INSERT INTO api_call_records
    (id, client_code, api_name, trace_id, request_status, http_status, error_code, duration_ms, called_at)
VALUES
    (1, 'CLIENT-DEMO-01', 'queryOrder', 'TRACE-API-001', 'SUCCESS', 200, NULL, 86, '2026-07-06 11:51:00.000'),
    (2, 'CLIENT-DEMO-01', 'queryOrder', 'TRACE-API-002', 'FAILED', 503, 'UPSTREAM_TIMEOUT', 2001, '2026-07-06 11:52:00.000'),
    (3, 'CLIENT-DEMO-01', 'queryOrder', 'TRACE-API-003', 'FAILED', 503, 'UPSTREAM_TIMEOUT', 2003, '2026-07-06 11:54:00.000'),
    (4, 'CLIENT-DEMO-01', 'queryOrder', 'TRACE-API-004', 'FAILED', 503, 'CIRCUIT_OPEN', 12, '2026-07-06 11:56:00.000'),
    (5, 'CLIENT-DEMO-01', 'queryOrder', 'TRACE-API-005', 'FAILED', 503, 'CIRCUIT_OPEN', 11, '2026-07-06 11:58:00.000'),
    (6, 'CLIENT-DEMO-01', 'queryOrder', 'TRACE-API-006', 'FAILED', 503, 'CIRCUIT_OPEN', 10, '2026-07-06 11:59:00.000');

-- Scenario 7: order and email are valid; a company invoice lacks a tax number.
INSERT INTO invoice_applications
    (id, invoice_no, order_id, invoice_type, title, tax_no, email_masked,
     qualification_status, issue_status, failure_code, created_at)
VALUES
    (1, 'I202607060007', 7, 'COMPANY', '示例科技有限公司', NULL, 'invoice***@example.com',
     'VERIFIED', 'FAILED', 'MISSING_TAX_NO', '2026-07-06 12:05:00.000');

INSERT INTO sop_definitions
    (id, scenario_type, title, audience, version, content_json, enabled)
VALUES
    (1, 'PAYMENT_SUCCESS_ORDER_PENDING', 'Payment callback recovery', '客服 / 技术支持 / 所有渠道', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Call','tool','OrderQueryTool','text','查询订单状态和最近更新时间。','ruleExpression',NULL),
       JSON_OBJECT('order','2.','action','Compare','tool','PaymentQueryTool','text','确认支付成功且金额与订单一致。','ruleExpression','payment.status == SUCCESS'),
       JSON_OBJECT('order','3.','action','Recover','tool','PaymentRecoveryJob','text','提交幂等的订单状态补偿任务。','ruleExpression','callback.status == FAILED')
     ), 1),
    (2, 'ORDER_CANCELLED_BUT_CHARGED', 'Cancelled order refund recovery', '客服 / 财务支持', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Verify','tool','OrderQueryTool','text','确认订单已取消且支付成功。','ruleExpression','order.status == CANCELLED'),
       JSON_OBJECT('order','2.','action','Query','tool','RefundQueryTool','text','检查退款任务与处理时限。','ruleExpression',NULL),
       JSON_OBJECT('order','3.','action','Escalate','tool','RefundRecoveryJob','text','补发退款任务，不承诺实际到账时间。','ruleExpression','refund.status != SUCCESS')
     ), 1),
    (3, 'COUPON_UNAVAILABLE', 'Coupon eligibility explanation', '客服 / 营销运营', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Verify','tool','CouponQueryTool','text','核对归属、状态和有效期。','ruleExpression',NULL),
       JSON_OBJECT('order','2.','action','Compare','tool','OrderQueryTool','text','比较订单金额和适用商品范围。','ruleExpression','payableAmount >= threshold'),
       JSON_OBJECT('order','3.','action','Explain','tool','ReplyTemplate','text','说明未满足的具体规则。','ruleExpression',NULL)
     ), 1),
    (4, 'MEMBER_BENEFIT_NOT_RECEIVED', 'Member benefit grant recovery', '客服 / 会员运营', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Verify','tool','MemberQueryTool','text','确认会员状态和有效期。','ruleExpression','member.status == ACTIVE'),
       JSON_OBJECT('order','2.','action','Query','tool','BenefitQueryTool','text','查询目标权益发放记录。','ruleExpression',NULL),
       JSON_OBJECT('order','3.','action','Recover','tool','BenefitGrantJob','text','对失败记录提交幂等补发。','ruleExpression','grant.status == FAILED')
     ), 1),
    (5, 'LOGISTICS_STATUS_NOT_SYNCED', 'Logistics status synchronization', '客服 / 物流运营', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Query','tool','LocalLogisticsQuery','text','读取平台最新物流节点。','ruleExpression',NULL),
       JSON_OBJECT('order','2.','action','Compare','tool','CarrierLogisticsQuery','text','比较承运商节点时间和状态。','ruleExpression','carrier.eventTime > local.eventTime'),
       JSON_OBJECT('order','3.','action','Sync','tool','LogisticsSyncJob','text','触发指定运单同步。','ruleExpression','carrier.status != local.status')
     ), 1),
    (6, 'API_FREQUENT_FAILURE', 'API failure window analysis', '技术支持 / 开放平台运营', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Aggregate','tool','ApiCallQueryTool','text','统计十分钟窗口的成功率。','ruleExpression','window == 10m'),
       JSON_OBJECT('order','2.','action','Group','tool','ApiCallQueryTool','text','按错误码统计分布。','ruleExpression',NULL),
       JSON_OBJECT('order','3.','action','Escalate','tool','IncidentChannel','text','达到阈值时关联服务事件。','ruleExpression','failureRate >= 0.5')
     ), 1),
    (7, 'INVOICE_ISSUE_FAILED', 'Invoice qualification validation', '客服 / 财务支持', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','Verify','tool','OrderQueryTool','text','确认订单完成且具备开票资格。','ruleExpression','order.status == COMPLETED'),
       JSON_OBJECT('order','2.','action','Validate','tool','InvoiceQueryTool','text','校验抬头、税号和接收邮箱。','ruleExpression',NULL),
       JSON_OBJECT('order','3.','action','Request','tool','ReplyTemplate','text','仅请求补充缺失字段，不虚构开票结果。','ruleExpression','invoice.status == FAILED')
     ), 1);
