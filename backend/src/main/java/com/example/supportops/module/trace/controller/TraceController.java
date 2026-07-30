package com.example.supportops.module.trace.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.trace.model.TraceModels;
import com.example.supportops.module.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 产品溯源 REST API；所有接口均受 JWT 保护。 */
@RestController
@RequestMapping("/api/v1/trace")
public class TraceController {
    private final TraceService service;

    public TraceController(TraceService service) {
        this.service = service;
    }

    @Operation(summary = "查询产品溯源总览")
    @GetMapping("/overview")
    public ApiResponse<TraceModels.Overview> overview(HttpServletRequest request) {
        return ok(service.overview(), request);
    }

    @GetMapping("/products")
    public ApiResponse<List<TraceModels.Product>> products(@RequestParam(required = false) String keyword,
                                                            HttpServletRequest request) {
        return ok(service.products(keyword), request);
    }

    @GetMapping("/purchases")
    public ApiResponse<List<TraceModels.Purchase>> purchases(@RequestParam(required = false) String purchaseNo,
                                                              @RequestParam(required = false) String status,
                                                              HttpServletRequest request) {
        return ok(service.purchases(purchaseNo, status), request);
    }

    @GetMapping("/batches")
    public ApiResponse<List<TraceModels.ProductionBatch>> batches(@RequestParam(required = false) String batchNo,
                                                                   @RequestParam(required = false) String status,
                                                                   HttpServletRequest request) {
        return ok(service.batches(batchNo, status), request);
    }

    @GetMapping("/quality")
    public ApiResponse<List<TraceModels.QualityInspection>> quality(@RequestParam(required = false) String inspectionNo,
                                                                     @RequestParam(required = false) String result,
                                                                     HttpServletRequest request) {
        return ok(service.inspections(inspectionNo, result), request);
    }

    @GetMapping("/inventory")
    public ApiResponse<List<TraceModels.Inventory>> inventory(@RequestParam(required = false) String referenceNo,
                                                               @RequestParam(required = false) String status,
                                                               HttpServletRequest request) {
        return ok(service.inventory(referenceNo, status), request);
    }

    @Operation(summary = "新增入库单并由后端生成溯源码")
    @PostMapping("/inventory/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TraceModels.Inventory> createInbound(@Valid @RequestBody TraceModels.InboundOrderCreate body,
                                                             HttpServletRequest request) {
        return ok(service.createInbound(body), request);
    }

    @GetMapping("/logistics")
    public ApiResponse<List<TraceModels.Logistics>> logistics(@RequestParam(required = false) String trackingNo,
                                                               HttpServletRequest request) {
        return ok(service.logistics(trackingNo), request);
    }

    @GetMapping("/sales")
    public ApiResponse<List<TraceModels.Sale>> sales(@RequestParam(required = false) String orderNo,
                                                      HttpServletRequest request) {
        return ok(service.sales(orderNo), request);
    }

    @GetMapping("/tickets")
    public ApiResponse<List<TraceModels.AfterSaleTicket>> tickets(@RequestParam(required = false) String ticketNo,
                                                                   @RequestParam(required = false) String status,
                                                                   HttpServletRequest request) {
        return ok(service.tickets(ticketNo, status), request);
    }

    @GetMapping("/recalls")
    public ApiResponse<List<TraceModels.Recall>> recalls(@RequestParam(required = false) String batchNo,
                                                         @RequestParam(required = false) String riskLevel,
                                                         HttpServletRequest request) {
        return ok(service.recalls(batchNo, riskLevel), request);
    }

    @Operation(summary = "按溯源码、序列号、批次、订单或运单查询完整生命周期")
    @GetMapping("/search/{code}")
    public ApiResponse<TraceModels.TraceDetail> trace(@PathVariable String code, HttpServletRequest request) {
        return ok(service.trace(code), request);
    }

    private <T> ApiResponse<T> ok(T value, HttpServletRequest request) {
        return ApiResponse.success(value, RequestIdFilter.getRequestId(request));
    }
}
