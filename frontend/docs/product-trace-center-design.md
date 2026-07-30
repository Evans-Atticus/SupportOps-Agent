# 产品溯源中心设计方案

## 1. 页面结构

页面采用企业供应链系统常用的“顶部全局栏 + 左侧业务树 + 右侧单模块工作区”结构，颜色、圆角和渐变沿用现有首页及智能体工作台。

左侧模块固定为：

1. 溯源总览
2. 产品档案
3. 供应商与采购
4. 生产批次
5. 质量检验
6. 仓储库存
7. 物流运输
8. 销售流向
9. 售后工单
10. 风险召回
11. 溯源查询

## 2. 核心关联键

完整溯源链以以下标识建立关系：

- `productCode`：产品/SKU 编码。
- `traceCode`：对外的一物一码追溯标识。
- `serialNo`：单件产品序列号。
- `batchNo`：生产批次号。
- `materialBatchNo`：原料批次号。
- `purchaseNo`：采购单号。
- `warehouseTaskNo`：出入库任务号。
- `trackingNo`：物流运单号。
- `orderNo`：销售订单号。
- `ticketNo`：售后工单号。
- `recallNo`：召回任务号。

各外部系统返回数据后，后端必须转换为内部标准模型，页面不直接依赖某家 ERP、MES、QMS、WMS 或 TMS 的原始字段。

## 3. 建议接口

| 模块 | 建议接口 | 外部数据源 |
|---|---|---|
| 溯源总览 | `GET /api/v1/trace/overview` | 聚合服务 |
| 产品档案 | `GET /api/v1/trace/products` | ERP/PIM |
| 供应商与采购 | `GET /api/v1/trace/purchases` | ERP/SRM |
| 生产批次 | `GET /api/v1/trace/batches/{batchNo}` | MES |
| 质量检验 | `GET /api/v1/trace/quality?batchNo=` | QMS |
| 仓储库存 | `GET /api/v1/trace/inventory?batchNo=` | WMS |
| 物流运输 | `GET /api/v1/trace/logistics/{trackingNo}` | TMS/承运商 |
| 销售流向 | `GET /api/v1/trace/sales?batchNo=` | ERP/OMS |
| 售后工单 | `GET /api/v1/tickets?businessNo=` | 工单平台 |
| 风险召回 | `POST /api/v1/trace/recalls` | 溯源聚合服务 |
| 溯源查询 | `GET /api/v1/trace/search?code=` | 溯源聚合服务 |

## 4. 与智能体的结合

前端沿用现有诊断接口：

```http
POST /api/v1/diagnoses
Idempotency-Key: <客户端生成的幂等键>
Content-Type: application/json
```

```json
{
  "ticketNo": "TK-0706-005",
  "businessNo": "O202607060005",
  "description": "结合序列号 SN-A018-00462 和批次 LOT-20260705-A18 分析履约异常",
  "scenarioType": "LOGISTICS_STATUS_NOT_SYNCED"
}
```

安全的数据流为：

```text
溯源页面选择对象
  -> 只提交 ticketNo、businessNo、description、scenarioType
  -> Java 后端按 businessNo 解析关联产品、批次和序列号
  -> 并行查询 ERP、MES、QMS、WMS、TMS 与工单平台
  -> 标准化并固化只读 EvidenceSnapshot
  -> Java Handler 判断根因、证据、SOP 和 Procedure
  -> 大模型只理解问题并润色客服回复
```

不能将浏览器页面展示的库存数量、检验结果、物流状态直接作为诊断事实，因为浏览器数据可以被修改。真实诊断必须由后端重新取证。

## 5. 后端扩展点

现有后端已具备工单、订单、支付、退款、物流和发票等数据查询能力。产品溯源落地还需增加：

- `ProductGateway`：产品档案和包装层级。
- `PurchaseGateway`：供应商、采购单和原料批次。
- `ProductionBatchGateway`：生产批次、投料及工序事件。
- `QualityGateway`：检验单、不合格项和处置记录。
- `WarehouseGateway`：库存、库位及出入库事件。
- `TraceAggregateService`：按任意追溯标识组装统一生命周期。
- `RecallService`：计算影响范围并执行冻结、通知和召回流程。
- 对应的 Java Handler：生产异常、质量异常、仓储异常和批次召回分析。

所有外部平台适配器均应支持超时、熔断、有限重试、请求追踪和独立降级；单个数据源失败时保留其他已验证证据，并将诊断结果标记为降级成功。

## 6. 实施顺序

1. 建立产品、批次、序列号和业务单号的统一关联模型。
2. 接入产品档案、生产批次、质量、仓储和物流只读查询。
3. 完成溯源查询与生命周期事件流。
4. 接入售后工单，并从溯源页面创建诊断任务。
5. 增加质量、仓储和召回 Java Handler。
6. 最后开放冻结库存、重推物流、创建召回等写操作，并增加审批和审计。
