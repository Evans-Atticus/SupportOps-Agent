package com.example.supportops.module.trace.service;

import com.example.supportops.module.trace.integration.TraceDataGateway;
import com.example.supportops.module.trace.model.TraceModels;
import org.springframework.stereotype.Service;

import java.util.List;

/** 产品溯源应用边界：负责查询编排，Controller 不直接访问平台 Gateway。 */
@Service
public class TraceService {
    private final TraceDataGateway gateway;

    public TraceService(TraceDataGateway gateway) {
        this.gateway = gateway;
    }

    public TraceModels.Overview overview() { return gateway.overview(); }
    public List<TraceModels.Product> products(String keyword) { return gateway.products(keyword); }
    public List<TraceModels.Purchase> purchases(String purchaseNo, String status) { return gateway.purchases(purchaseNo, status); }
    public List<TraceModels.ProductionBatch> batches(String batchNo, String status) { return gateway.batches(batchNo, status); }
    public List<TraceModels.QualityInspection> inspections(String inspectionNo, String result) { return gateway.inspections(inspectionNo, result); }
    public List<TraceModels.Inventory> inventory(String referenceNo, String status) { return gateway.inventory(referenceNo, status); }
    public TraceModels.Inventory createInbound(TraceModels.InboundOrderCreate request) { return gateway.createInbound(request); }
    public List<TraceModels.Logistics> logistics(String trackingNo) { return gateway.logistics(trackingNo); }
    public List<TraceModels.Sale> sales(String orderNo) { return gateway.sales(orderNo); }
    public List<TraceModels.AfterSaleTicket> tickets(String ticketNo, String status) { return gateway.tickets(ticketNo, status); }
    public List<TraceModels.Recall> recalls(String batchNo, String riskLevel) { return gateway.recalls(batchNo, riskLevel); }
    public TraceModels.TraceDetail trace(String code) { return gateway.trace(code); }
}
