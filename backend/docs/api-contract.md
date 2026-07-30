# SupportOps Agent API 契约（初稿）

## 1. 通用约定

- Base path：`/api/v1`
- Content-Type：`application/json; charset=UTF-8`
- 鉴权：除登录、健康检查和 OpenAPI 外使用 `Authorization: Bearer <JWT>`（Day 7 启用）
- 时间：ISO-8601，例如 `2026-07-20T10:00:00+08:00`
- 幂等：创建诊断时客户端可传 `Idempotency-Key`
- 分页：`limit` 默认 20，范围 1～100

统一响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "6c19da10-5cc4-4743-bc87-cf9c8effd890",
  "timestamp": "2026-07-20T10:00:00+08:00"
}
```

HTTP 状态码表达传输层结果，`code` 表达稳定的业务错误。校验错误的 `data` 为字段到消息的映射。

## 2. 字段映射

| 前端字段 | API 字段 | 数据库字段 | 说明 |
| --- | --- | --- | --- |
| 工单号 | `ticketNo` | `tickets.ticket_no` | 唯一 |
| 业务号 | `businessNo` | 各业务表 `*_no` | 订单号、接口标识等 |
| 客户描述 | `description` | `tickets.description` | 1～2,000 字 |
| 诊断 ID | `diagnosisId` | `diagnosis_tasks.id` | `BIGINT` |
| 场景类型 | `scenarioType` | `scenario_type` | 白名单枚举 |
| 状态 | `status` | `status` | 见状态机 |
| 置信度 | `confidence` | `confidence` | 0～1 |
| 步骤 | `steps[]` | `diagnosis_steps` | 按 `step_order` 排序 |
| Procedure | `procedure` | `sop_definitions.content_json` | 面向工作台展示 |
| 证据 | `evidences[]` | `diagnosis_evidences` | 可追溯到表和字段 |
| 客服回复 | `customerReply` | `diagnosis_reports.customer_reply` | 不得新增事实 |

## 3. 认证

### `POST /api/v1/auth/register`

```json
{"username":"new_agent","displayName":"新客服","password":"SupportOps2026"}
```

注册账号固定为最小权限客服角色；同一 IP 一小时内最多成功登录 5 个不同账号。

### `POST /api/v1/auth/login`

请求：

```json
{"username":"demo","password":"SupportOps@2026"}
```

响应 `data`：

```json
{"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":7200}
```

### `GET /api/v1/auth/me`

响应 `data`：

```json
{"id":1,"username":"demo","displayName":"演示客服","roles":["SUPPORT_AGENT"]}
```

## 4. 诊断任务

### `POST /api/v1/diagnoses`

请求：

```json
{
  "ticketNo":"TK-0706-001",
  "businessNo":"O202607060001",
  "description":"客户已支付成功，但订单仍显示待支付，请诊断原因并生成客服回复。",
  "scenarioType":null
}
```

响应 `data`（HTTP 202）：

```json
{"diagnosisId":10001,"status":"PENDING","pollAfterMs":800,"reused":false}
```

### `GET /api/v1/diagnoses/{diagnosisId}`

运行中返回当前状态和已完成步骤；成功或降级成功时返回完整详情：

```json
{
  "diagnosisId":10001,
  "status":"SUCCESS",
  "scenarioType":"PAYMENT_SUCCESS_ORDER_PENDING",
  "scenarioName":"支付成功但订单未更新",
  "title":"Payment callback recovery",
  "summary":"支付成功但订单状态未同步。",
  "confidence":0.94,
  "steps":[{"code":"UNDERSTAND_TICKET","title":"理解客户问题","status":"SUCCESS","durationMs":420}],
  "procedure":{"title":"Payment callback recovery","audience":"客服 / 技术支持 / 所有渠道","instructions":[]},
  "evidences":[{"source":"payment_records","field":"payment_status","label":"支付状态","value":"SUCCESS","description":"支付流水已成功","confidence":1.0}],
  "conclusion":"支付回调失败导致订单状态未同步",
  "internalSuggestion":"触发订单状态补偿任务",
  "customerReply":"您好，经核查您的支付已经成功，我们正在同步订单状态。",
  "degraded":false,
  "errorCode":null,
  "errorMessage":null
}
```

### 其他接口

| 方法 | 路径 | 说明 | 成功状态 |
| --- | --- | --- | --- |
| GET | `/api/v1/diagnoses/{id}/report` | 获取完整报告 | 200 |
| GET | `/api/v1/diagnoses?limit=20` | 最近诊断 | 200 |
| POST | `/api/v1/diagnoses/{id}/apply` | 标记采用建议 | 200 |
| POST | `/api/v1/diagnoses/{id}/discard` | 丢弃未终结任务 | 200 |
| GET | `/api/v1/system/health` | 骨架健康检查 | 200 |

## 5. 前端轮询规则

客户端收到任务 ID 后每 800ms 查询一次。进入 `SUCCESS`、`FAILED`、`DEGRADED_SUCCESS` 或 `DISCARDED` 后停止；页面卸载或再次提交时取消旧请求。401 清除会话并跳转登录页，429 展示限额提示，其他错误展示响应中的 `message` 与 `requestId`。
