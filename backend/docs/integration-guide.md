# 外部平台接入指南

## 1. 设计目标

诊断 Handler 只依赖 `BusinessDataSource` 标准数据端口，不直接依赖 MySQL、ERP 或物流厂商 SDK。默认所有连接器关闭，现有七个演示场景继续从本地数据库读取。只打开某个平台的 `*_INTEGRATION_ENABLED` 即可对该数据域切换到 HTTP API。

## 2. 预留的平台和标准路由

| 平台 | 配置 | 默认标准路由 |
|---|---|---|
| ERP | `ERP_BASE_URL` | `/customers/{customerNo}`、`/orders/{orderNo}`、`/orders/{orderNo}/payments`、`/refunds`、`/invoices`、`/coupons/{couponCode}` |
| 物流 | `LOGISTICS_BASE_URL` | `/tracking/{trackingNo}`、`/orders/{orderNo}/logistics` |
| 会员 | `MEMBERSHIP_BASE_URL` | `/members/{memberNo}` |
| API 监控 | `MONITORING_BASE_URL` | `/api-calls?clientCode=...&apiName=...&limit=...` |
| 工单 | `TICKETING_BASE_URL` | `/tickets/{ticketNo}` |

响应可以是 JSON 数组，也可以使用 `{ "data": [...] }` 或 `{ "data": { "records": [...] } }` 外壳。字段需映射到 `module.business.model.query` 中的标准 record。如果厂商契约不同，在 `ConfigurableBusinessDataSource` 或新的厂商适配器中转换，不要修改 Handler。

## 3. 外部工单导入

1. 配置 `TICKETING_BASE_URL` 和 `TICKETING_API_TOKEN`。
2. 设置 `TICKETING_INTEGRATION_ENABLED=true`。
3. 带 JWT 调用 `POST /api/v1/integrations/tickets/{ticketNo}/import`。
4. 后端从工单平台取得 `businessNo`，幂等保存本地工单，然后可调用原有诊断 API。

外部工单标准字段为 `customerId`、`ticketNo`、`businessNo`、`channel`、`description`、`scenarioHint`、`priority`。真实项目中建议再增加“外部客户编号 -> 内部客户 ID”映射表。

## 4. 安全与可用性

- Token 仅通过后端环境变量注入，不进入前端、Git、数据库或日志。
- 连接和读取超时默认为 3 秒和 8 秒。
- 不向客户返回第三方原始响应体，统一转换为 `INTEGRATION_UNAVAILABLE`。
- `GET /api/v1/integrations/status` 只返回连接器开关和配置完整性，不暴露 URL 和 Token。
