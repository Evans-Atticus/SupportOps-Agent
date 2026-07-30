# 第三周：LangChain4j AI、异步诊断与降级设计

## 完成范围

- `TicketUnderstandingAiService`：将工单描述解析成 `TicketIntent`。
- `CustomerReplyAiService`：只根据已验证、已脱敏的诊断上下文生成回复。
- Mock/Real 条件装配：业务代码不感知模型供应商。
- Bean Validation：校验场景、摘要、情绪、置信度和回复长度。
- 关键词分类与七场景安全模板：模型失败时保持规则报告可用。
- 异步五阶段状态机：理解、查询、规则诊断、回复、报告。
- `ChatModelListener` 与调用审计：保存模型、Token、耗时和错误，不保存 Prompt。
- 用户每日额度、幂等键、五分钟重复报告复用和单任务最多两次模型调用。

## 主流程

```text
POST /api/v1/diagnoses
  -> 校验用户每日额度与重复请求
  -> 创建 PENDING 任务和五个 PENDING 步骤
  -> HTTP 202 立即返回 diagnosisId
  -> diagnosis 线程池后台执行：
       UNDERSTANDING     第 1 次 AI 调用，输出 TicketIntent
       QUERYING          按白名单查询业务快照
       DIAGNOSING        Java Handler 生成根因和证据
       GENERATING_REPLY  第 2 次 AI 调用，只输入脱敏事实
       SUCCESS / DEGRADED_SUCCESS
```

轮询接口在报告尚未生成时使用 `LEFT JOIN` 返回当前任务和步骤，因此不会把运行中的任务误报为不存在。

## AI 与 Java 的职责边界

AI 可以：

- 识别七个场景或 `UNKNOWN`；
- 抽取文本中明确出现的业务号；
- 概括问题、判断情绪；
- 润色已经验证的客服回复。

AI 不可以：

- 查询数据库或选择任意工具；
- 决定最终根因；
- 修改证据、SOP 和内部建议；
- 承诺未执行的退款、补发、赔偿或到账时间。

最终根因始终由七个 `ScenarioDiagnosisHandler` 根据业务快照计算。

## Mock 与 Real

### 默认开发模式

```dotenv
SPRING_PROFILES_ACTIVE=real
AI_MODE=mock
AI_API_KEY=qianwen
```

该组合使用真实 MySQL，但不请求模型服务，适合后端开发和前后端联调。

### 前后端联调完成后启用千问

```dotenv
SPRING_PROFILES_ACTIVE=real
AI_MODE=real
AI_API_KEY=qianwen  # 联调部署时替换为有效百炼 Key
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_MODEL=glm-5.2
```

切换时无需修改 Java 代码。`AiServicesConfig` 会构建两个 LangChain4j AI Service，Mock Bean 自动退出。

不同百炼套餐的 Key 与 Base URL 必须配套；模型 ID 也以账号所在地域和控制台实际可用项为准。

## 降级路径

```text
理解模型超时 / 429 / 服务异常 / JSON 解析失败
  -> 记录一次失败调用
  -> 使用工单可信 scenarioHint + 关键词分类
  -> Java 规则继续生成报告

回复模型失败
  -> 记录第二次失败调用
  -> 使用七场景安全模板
  -> 最终状态 DEGRADED_SUCCESS
```

若降级分类仍为 `UNKNOWN` 或业务号缺失，任务进入 `FAILED` 并返回稳定 `errorCode`。

## 审计与成本控制

`model_call_logs` 只保存：诊断 ID、requestId、调用类型、供应商、模型名、状态、Token、耗时和错误码。完整 Prompt、模型回复、API Key 不进入日志表。

每次调用前通过条件更新预占次数：

```sql
UPDATE diagnosis_tasks
SET model_call_count = model_call_count + 1
WHERE id = ? AND model_call_count < 2;
```

更新失败即拒绝第三次调用；数据库 CHECK 约束再提供一道最终保护。

## 关键代码

- AI Service：`module/ai/understanding`、`module/ai/reply`
- Mock：`module/ai/mock`
- Real 装配：`module/ai/config/AiServicesConfig.java`
- 调用监听：`module/ai/audit/SupportOpsChatModelListener.java`
- 异步状态机：`module/diagnosis/application/DiagnosisTaskProcessor.java`
- 任务提交与轮询：`DiagnosisApplicationService.java`

## 已完成验证

- 22 个 Maven 测试通过。
- 7 个演示工单均先返回 `PENDING`，随后进入 `SUCCESS`。
- 每个任务恰好两次 Mock AI 调用，证据数为 2–4 条。
- Real 模式连接不可用端点时进入 `DEGRADED_SUCCESS`，规则证据和模板回复仍完整保存。
- 有效千问 Key 留到前后端联调阶段配置。
