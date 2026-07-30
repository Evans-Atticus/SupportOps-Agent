# 工单中心页面设计方案

## 1. 页面目标

工单中心是“真实业务数据”和“智能诊断”之间的操作层。客服先在此查看工单、客户和业务事实，再将 `ticketNo + businessNo` 送入诊断平台。

## 2. 信息架构

- 左侧：工单搜索、状态筛选、优先级和队列。
- 中间：工单摘要、客户诉求、订单、支付、物流和仓储四个业务模块。
- 右侧：诊断就绪度、缺失证据、数据源状态和操作时间线。
- 主操作：“发起智能诊断”只传递工单号、业务号和客户原始描述，根因仍由后端 Java Handler 产生。
- 工单构建：支持选择客户、业务类型、优先级、业务号和原始问题，创建后进入待诊断队列。
- 产品追溯：物流模块同时展示 SKU、批次、出库仓、包裹和承运节点，支持从客户问题反查产品履约链路。
- 支付与发票：在同一业务域内查看资金流水、支出和发票申请，避免客服在多个后台间来回查询。

## 3. 后续 API 映射

| 页面区域 | 数据来源 | 建议接口 |
|---|---|---|
| 工单队列 | 工单平台 / 本地工单库 | `GET /api/v1/tickets` |
| 工单详情 | 工单平台 | `GET /api/v1/tickets/{id}` |
| 订单信息 | ERP | `GET /api/v1/business/orders/{orderNo}` |
| 支付信息 | ERP / 支付平台 | `GET /api/v1/business/orders/{orderNo}/payments` |
| 物流信息 | 物流平台 | `GET /api/v1/business/logistics/{trackingNo}` |
| 仓储信息 | WMS | 后续新增 `GET /api/v1/business/orders/{orderNo}/warehouse` |
| 产品追溯 | WMS / OMS / 物流平台 | 后续新增 `GET /api/v1/business/products/{traceCode}/trace` |
| 发票信息 | ERP / 税务平台 | `GET /api/v1/business/orders/{orderNo}/invoices` |
| 资金流水 | 支付平台 | 后续新增 `GET /api/v1/business/accounts/{accountNo}/transactions` |
| 发起诊断 | 诊断平台 | `POST /api/v1/diagnoses` |

## 4. 组件拆分建议

- `TicketCenterView.vue`：页面编排与选中工单状态。
- `TicketQueue.vue`：搜索、筛选、分页和队列。
- `TicketBuilder.vue`：构建工单、客户与业务号校验。
- `TicketSummary.vue`：工单、客户和 SLA 摘要。
- `OrderPanel.vue`、`PaymentPanel.vue`、`LogisticsPanel.vue`、`WarehousePanel.vue`：四个独立业务模块。
- `ProductTraceTimeline.vue`：用 SKU / 批次 / 运单组装产品履约追溯链。
- `AccountLedgerTabs.vue`：资金流水和发票联合查询。
- `DiagnosisReadiness.vue`：证据完整度与诊断入口。
- `ticket-center.js`：并发加载各数据源，通过 `AbortController` 取消过期工单请求。

## 5. 交互与状态

- 选择新工单时，四个业务模块并行加载，每个模块独立显示 loading / empty / error / success。
- 任意外部平台失败不影响其他模块，右侧标记缺失证据。
- 证据不完整时仍可发起诊断，但需显示降级警告；后端最终决定是否可诊断。
- 诊断完成后保留 `diagnosisId`，支持从工单中心跳转报告、采纳或丢弃建议。

## 6. 与原工单诊断智能体的连接

```text
工单中心选中工单
  -> POST /api/v1/diagnoses { ticketNo, businessNo, description }
  -> 诊断后端根据 businessNo 重新读取可信业务数据
  -> ERP + 支付 + 物流 + WMS + 发票适配器
  -> DiagnosisContext 只读证据快照
  -> Java Handler 计算根因、证据、SOP 和 Procedure
  -> AI 仅理解问题与润色客服回复
  -> 工单中心按 diagnosisId 查询并展示报告
```

前端已经展示的金额、状态和轨迹不作为诊断证据入参，防止页面数据被篡改。诊断任务始终在后端按业务号重新取数，并将当时的证据快照与报告绑定。

当前 Handler 已能分析订单、支付、退款、物流和发票数据。仓储和产品追溯属于新数据域，落地时需增加 `WarehouseRecord`、`ProductTraceRecord` 及对应 Handler，再注册到现有场景白名单。

## 7. 建议功能与实施优先级

### P0：首期可用

1. **工单构建与导入**：手工建单、从外部客服平台导入、重复工单检测。
2. **联合搜索**：使用工单号、订单号、支付单号、运单号、发票号或客户号查询。
3. **四域数据聚合**：订单、支付/账户、物流/产品追溯、仓储数据独立加载。
4. **跨系统冲突检测**：自动标记“支付成功但订单待支付”、“承运商签收但平台运输中”等事实冲突。
5. **一键发起诊断**：连接现有 `/api/v1/diagnoses`，轮询任务状态并展示根因、证据、SOP、Procedure 和客服回复。
6. **SLA 与责任人**：优先级、剩余时间、分配、转交、状态流转和处理备注。

### P1：处置闭环

1. **诊断后动作**：幂等重试支付回调、重推物流节点、重建 WMS 任务。
2. **审批门禁**：退款、补偿、补发和改价等高风险动作必须人工确认。
3. **客服回复工作区**：查看 AI 草稿、引用证据、人工编辑、复制或回传客服平台。
4. **完整审计时间线**：记录谁查询、诊断、采纳和执行了什么，保留 requestId / diagnosisId。

### P2：平台化增强

1. **相似工单聚类**：识别批量故障，避免同一问题反复诊断和消耗 Token。
2. **事件订阅**：订单、物流或支付状态变化时自动刷新工单，减少人工轮询。
3. **规则运营看板**：各 Handler 命中率、降级率、平均处理时间和证据缺失率。
4. **多租户与数据权限**：按组织、店铺、仓库和客服组限制数据可见范围。
