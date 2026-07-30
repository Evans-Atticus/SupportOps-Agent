package com.example.supportops.module.trace.integration;

import com.example.supportops.module.trace.model.TraceModels;

import java.util.List;

/** ERP/MES/QMS/WMS/TMS 的防腐层；真实平台适配器只需实现此接口。 */
public interface TraceDataGateway {
    TraceModels.Overview overview();
    List<TraceModels.Product> products(String keyword);
    List<TraceModels.Purchase> purchases(String purchaseNo, String status);
    List<TraceModels.ProductionBatch> batches(String batchNo, String status);
    List<TraceModels.QualityInspection> inspections(String inspectionNo, String result);
    List<TraceModels.Inventory> inventory(String referenceNo, String status);
    TraceModels.Inventory createInbound(TraceModels.InboundOrderCreate request);
    List<TraceModels.Logistics> logistics(String trackingNo);
    List<TraceModels.Sale> sales(String orderNo);
    List<TraceModels.AfterSaleTicket> tickets(String ticketNo, String status);
    List<TraceModels.Recall> recalls(String batchNo, String riskLevel);
    TraceModels.TraceDetail trace(String code);
}
