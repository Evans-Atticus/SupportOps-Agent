ALTER TABLE logistics_records
    ADD COLUMN carrier_name VARCHAR(80) NULL AFTER source_type,
    ADD COLUMN origin_location VARCHAR(160) NULL AFTER status_description,
    ADD COLUMN destination_location VARCHAR(160) NULL AFTER origin_location,
    ADD COLUMN current_location VARCHAR(160) NULL AFTER destination_location,
    ADD COLUMN facility_name VARCHAR(120) NULL AFTER current_location,
    ADD COLUMN courier_name_masked VARCHAR(60) NULL AFTER facility_name,
    ADD COLUMN courier_phone_masked VARCHAR(32) NULL AFTER courier_name_masked,
    ADD COLUMN estimated_delivery_at DATETIME(3) NULL AFTER courier_phone_masked;

UPDATE logistics_records
   SET carrier_name='顺丰速运',
       status_description='包裹已从上海浦东分拨中心发出',
       origin_location='上海市浦东新区', destination_location='江苏省苏州市工业园区',
       current_location='上海市浦东新区', facility_name='上海浦东分拨中心',
       estimated_delivery_at='2026-07-28 18:00:00.000',
       event_time='2026-07-27 20:05:00.000', synced_at='2026-07-27 20:06:00.000'
 WHERE tracking_no='SF202607280202' AND source_type='LOCAL';

UPDATE logistics_records
   SET carrier_name='顺丰速运',
       status_description='快递员正在苏州市工业园区金鸡湖街道派送，请保持电话畅通',
       origin_location='上海市浦东新区', destination_location='江苏省苏州市工业园区',
       current_location='江苏省苏州市工业园区金鸡湖街道', facility_name='顺丰苏州工业园区营业点',
       courier_name_masked='李师傅', courier_phone_masked='138****6208',
       estimated_delivery_at='2026-07-28 18:00:00.000'
 WHERE tracking_no='SF202607280202' AND source_type='CARRIER' AND logistics_status='OUT_FOR_DELIVERY';

INSERT INTO logistics_records
    (tracking_no, order_id, source_type, carrier_name, logistics_status, status_description,
     origin_location, destination_location, current_location, facility_name, estimated_delivery_at,
     event_time, created_at)
SELECT 'SF202607280202', o.id, 'CARRIER', '顺丰速运', 'PICKED_UP',
       '顺丰速运已揽收包裹', '上海市浦东新区', '江苏省苏州市工业园区',
       '上海市浦东新区张江镇', '顺丰上海张江营业点', '2026-07-28 18:00:00.000',
       '2026-07-27 18:20:00.000', '2026-07-27 18:20:05.000'
  FROM biz_orders o WHERE o.order_no='O202607280202'
   AND NOT EXISTS (SELECT 1 FROM logistics_records WHERE tracking_no='SF202607280202' AND source_type='CARRIER' AND logistics_status='PICKED_UP');

INSERT INTO logistics_records
    (tracking_no, order_id, source_type, carrier_name, logistics_status, status_description,
     origin_location, destination_location, current_location, facility_name, estimated_delivery_at,
     event_time, created_at)
SELECT 'SF202607280202', o.id, 'CARRIER', '顺丰速运', 'IN_TRANSIT',
       '包裹已离开上海浦东分拨中心，发往苏州', '上海市浦东新区', '江苏省苏州市工业园区',
       '上海市浦东新区', '上海浦东分拨中心', '2026-07-28 18:00:00.000',
       '2026-07-27 20:00:00.000', '2026-07-27 20:00:05.000'
  FROM biz_orders o WHERE o.order_no='O202607280202'
   AND NOT EXISTS (SELECT 1 FROM logistics_records WHERE tracking_no='SF202607280202' AND source_type='CARRIER' AND logistics_status='IN_TRANSIT');

INSERT INTO logistics_records
    (tracking_no, order_id, source_type, carrier_name, logistics_status, status_description,
     origin_location, destination_location, current_location, facility_name, estimated_delivery_at,
     event_time, created_at)
SELECT 'SF202607280202', o.id, 'CARRIER', '顺丰速运', 'ARRIVED_TRANSIT',
       '包裹已到达苏州工业园区营业点，等待安排派送', '上海市浦东新区', '江苏省苏州市工业园区',
       '江苏省苏州市工业园区', '顺丰苏州工业园区营业点', '2026-07-28 18:00:00.000',
       '2026-07-28 09:10:00.000', '2026-07-28 09:10:05.000'
  FROM biz_orders o WHERE o.order_no='O202607280202'
   AND NOT EXISTS (SELECT 1 FROM logistics_records WHERE tracking_no='SF202607280202' AND source_type='CARRIER' AND logistics_status='ARRIVED_TRANSIT');

INSERT INTO sop_definitions
    (scenario_type, title, audience, version, content_json, enabled)
VALUES
    ('LOGISTICS_TRACKING_QUERY', '物流路线与节点查询', '客服 / 客户', 1,
     '[{"order":"1","action":"查询运单","tool":"LogisticsQueryService","text":"读取承运商最新节点和完整运输轨迹。"},{"order":"2","action":"核对路线","tool":"RouteFactBuilder","text":"核对发件地、目的地、当前位置与预计送达时间。"},{"order":"3","action":"回复客户","tool":"VerifiedReplyBuilder","text":"按真实物流节点直接回复，不推测未回传的位置。"}]', TRUE)
ON DUPLICATE KEY UPDATE title=VALUES(title), audience=VALUES(audience), content_json=VALUES(content_json), enabled=TRUE;
