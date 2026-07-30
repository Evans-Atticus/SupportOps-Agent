# SupportOps Agent 错误码

错误码是稳定契约；消息可本地化，客户端不得依赖消息文本判断分支。

| HTTP | 错误码 | 含义 | 客户端建议 |
| --- | --- | --- | --- |
| 200 | `OK` | 成功 | 正常处理 |
| 400 | `INVALID_ARGUMENT` | 请求字段校验失败 | 标记对应字段 |
| 400 | `MISSING_BUSINESS_NO` | 当前场景缺少业务号 | 提示补充订单号等 |
| 400 | `UNKNOWN_SCENARIO` | 无法映射到 7 个场景 | 转人工或补充描述 |
| 401 | `UNAUTHORIZED` | 未登录或令牌无效 | 清理会话并登录 |
| 403 | `FORBIDDEN` | 无权执行操作 | 禁止重试 |
| 404 | `RESOURCE_NOT_FOUND` | 通用资源不存在 | 返回列表或提示 |
| 404 | `TICKET_NOT_FOUND` | 工单不存在 | 检查工单号 |
| 404 | `DIAGNOSIS_NOT_FOUND` | 诊断任务不存在 | 停止轮询 |
| 409 | `INVALID_STATUS_TRANSITION` | 非法状态迁移 | 刷新任务状态 |
| 409 | `DUPLICATE_REQUEST` | 幂等键冲突 | 使用原任务结果 |
| 429 | `DAILY_QUOTA_EXCEEDED` | 每日演示额度耗尽 | 次日再试 |
| 429 | `AI_RATE_LIMITED` | 模型限流 | 进入模板降级 |
| 503 | `AI_UNAVAILABLE` | 模型服务不可用 | 进入模板降级 |
| 504 | `AI_TIMEOUT` | 模型调用超时 | 进入模板降级 |
| 500 | `AI_RESPONSE_PARSE_FAILED` | 模型结构化输出解析失败 | 使用关键词/模板降级 |
| 500 | `DATABASE_ERROR` | 数据库操作失败 | 携 requestId 报障 |
| 500 | `INTERNAL_ERROR` | 未分类内部错误 | 携 requestId 报障 |

## 异常映射原则

- Bean Validation 统一映射为 `INVALID_ARGUMENT`，`data` 返回字段错误。
- 业务异常使用预定义错误码，不暴露 SQL、类名或堆栈。
- 模型错误仅在规则报告已经生成时转为 `DEGRADED_SUCCESS`；否则保留失败状态。
- 日志记录错误码、requestId、diagnosisId 和异常类型，但不记录 API Key、完整 Prompt、令牌或客户敏感信息。
