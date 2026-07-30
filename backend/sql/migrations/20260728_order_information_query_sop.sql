INSERT INTO sop_definitions
    (scenario_type, title, audience, version, content_json, enabled)
VALUES
    ('ORDER_INFORMATION_QUERY', '订单信息查询', '客服 / 所有渠道', 1,
     JSON_ARRAY(
       JSON_OBJECT('order','1.','action','理解需求','tool','智能意图理解','text','理解客户要查询的具体订单字段，历史场景仅作为线索。','ruleExpression','businessNo != null'),
       JSON_OBJECT('order','2.','action','查询订单','tool','订单信息查询','text','读取客户所选订单的真实业务快照。','ruleExpression','order.customerNo == currentCustomerNo'),
       JSON_OBJECT('order','3.','action','生成回复','tool','可信回复生成','text','根据已查询事实直接回答，不擅自判断异常。','ruleExpression','evidence != null')
     ), 1)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    audience = VALUES(audience),
    version = VALUES(version),
    content_json = VALUES(content_json),
    enabled = VALUES(enabled);
