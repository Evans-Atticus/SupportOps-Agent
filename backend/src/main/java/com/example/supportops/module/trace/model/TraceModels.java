package com.example.supportops.module.trace.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 产品溯源模块的稳定 API 模型；外部平台字段必须先转换为这些标准模型。 */
public final class TraceModels {
    private TraceModels() {
    }

    public record Overview(long products, long eventsToday, long pendingAnomalies,
                           double traceabilityRate, List<SourceHealth> sources) {}

    public record SourceHealth(String source, String status, long latencyMs) {}

    public record Product(String productCode, String name, String specification, String category,
                          String traceMode, String packageLevel, long stockQuantity, String status) {}

    public record Purchase(String purchaseNo, String supplier, String productName, String materialBatchNo,
                           String receiptStatus, String qualityStatus, String productionBatchNo) {}

    public record ProductionBatch(String batchNo, String productCode, String factory, String productionLine,
                                  int plannedQuantity, int qualifiedQuantity, String status,
                                  LocalDateTime completedAt, List<String> processSteps) {}

    public record QualityInspection(String inspectionNo, String inspectionType, String batchNo,
                                    int sampleQuantity, int failedQuantity, String result,
                                    String inspector, LocalDateTime completedAt) {}

    public record Inventory(String referenceNo, String productCode, String batchNo, String warehouse,
                            String location, int quantity, int frozenQuantity, String status,
                            String type, String traceCode, LocalDateTime updatedAt) {}

    public record Logistics(String trackingNo, String orderNo, String productCode, String batchNo,
                            String serialNo, String carrier, String status, String latestLocation,
                            LocalDateTime eventTime) {}

    public record Sale(String orderNo, String channel, String customer, String productCode,
                       String batchNo, String serialNo, String region, int quantity, String status) {}

    public record AfterSaleTicket(String ticketNo, String problem, String businessNo, String productCode,
                                  String batchNo, String priority, String status, String owner) {}

    public record Recall(String recallNo, String batchNo, String productCode, int producedQuantity,
                         int affectedQuantity, String riskLevel, String status, List<RecallScope> scopes) {}

    public record RecallScope(String location, String objectType, int quantity, String status) {}

    public record TraceEvent(String eventId, String stage, String title, String source,
                             String sourceRecordNo, String location, LocalDateTime occurredAt, String status) {}

    public record TraceDetail(String traceCode, String productCode, String batchNo, String serialNo,
                              String currentStatus, List<TraceEvent> events) {}

    /** 入库单号允许为空，由后端生成；溯源码始终由后端生成，客户端不能提交。 */
    public record InboundOrderCreate(
            @Size(max = 40) String inboundNo,
            @NotBlank @Size(max = 40) String sourcePurchaseNo,
            @NotBlank @Size(max = 40) String productCode,
            @NotBlank @Size(max = 40) String batchNo,
            @NotBlank @Size(max = 60) String warehouse,
            @NotBlank @Size(max = 40) String location,
            @Min(1) int quantity,
            @NotBlank @Size(max = 30) String inboundType,
            @Size(max = 200) String remark) {}
}
