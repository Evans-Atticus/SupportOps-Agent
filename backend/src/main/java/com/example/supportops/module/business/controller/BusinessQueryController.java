package com.example.supportops.module.business.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.business.model.query.ApiCallRecord;
import com.example.supportops.module.business.model.query.BusinessQueryRecord;
import com.example.supportops.module.business.model.query.CouponRecord;
import com.example.supportops.module.business.model.query.CustomerRecord;
import com.example.supportops.module.business.model.query.InvoiceRecord;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.MemberRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.business.model.query.RefundRecord;
import com.example.supportops.module.business.model.query.SopRecord;
import com.example.supportops.module.business.model.vo.BusinessSnapshotVO;
import com.example.supportops.module.business.service.BusinessQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/business")
public class BusinessQueryController {
    private final BusinessQueryService businessQueryService;

    public BusinessQueryController(BusinessQueryService businessQueryService) {
        this.businessQueryService = businessQueryService;
    }

    @Operation(summary = "查询客户")
    @GetMapping("/customers/{customerNo}")
    public ApiResponse<BusinessSnapshotVO<CustomerRecord>> customer(
            @PathVariable String customerNo, HttpServletRequest request) {
        return ok(businessQueryService.customer(customerNo), request);
    }

    @Operation(summary = "查询订单")
    @GetMapping("/orders/{orderNo}")
    public ApiResponse<BusinessSnapshotVO<OrderRecord>> order(
            @PathVariable String orderNo, HttpServletRequest request) {
        return ok(businessQueryService.order(orderNo), request);
    }

    @Operation(summary = "按订单查询支付")
    @GetMapping("/orders/{orderNo}/payments")
    public ApiResponse<BusinessSnapshotVO<PaymentRecord>> payments(
            @PathVariable String orderNo, HttpServletRequest request) {
        return ok(businessQueryService.paymentsByOrder(orderNo), request);
    }

    @Operation(summary = "按订单查询退款")
    @GetMapping("/orders/{orderNo}/refunds")
    public ApiResponse<BusinessSnapshotVO<RefundRecord>> refunds(
            @PathVariable String orderNo, HttpServletRequest request) {
        return ok(businessQueryService.refundsByOrder(orderNo), request);
    }

    @Operation(summary = "查询客户优惠券")
    @GetMapping("/coupons/{couponCode}")
    public ApiResponse<BusinessSnapshotVO<CouponRecord>> coupon(
            @PathVariable String couponCode, @RequestParam String customerNo, HttpServletRequest request) {
        return ok(businessQueryService.coupon(couponCode, customerNo), request);
    }

    @Operation(summary = "查询会员和权益")
    @GetMapping("/members/{memberNo}")
    public ApiResponse<BusinessSnapshotVO<MemberRecord>> member(
            @PathVariable String memberNo, HttpServletRequest request) {
        return ok(businessQueryService.member(memberNo), request);
    }

    @Operation(summary = "查询本地和承运商物流")
    @GetMapping("/logistics/{trackingNo}")
    public ApiResponse<BusinessSnapshotVO<LogisticsRecord>> logistics(
            @PathVariable String trackingNo, HttpServletRequest request) {
        return ok(businessQueryService.logistics(trackingNo), request);
    }

    @Operation(summary = "查询 API 调用记录")
    @GetMapping("/api-calls/{clientCode}")
    public ApiResponse<BusinessSnapshotVO<ApiCallRecord>> apiCalls(
            @PathVariable String clientCode,
            @RequestParam(required = false) String apiName,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            HttpServletRequest request) {
        return ok(businessQueryService.apiCalls(clientCode, apiName, limit), request);
    }

    @Operation(summary = "按订单查询发票")
    @GetMapping("/orders/{orderNo}/invoices")
    public ApiResponse<BusinessSnapshotVO<InvoiceRecord>> invoice(
            @PathVariable String orderNo, HttpServletRequest request) {
        return ok(businessQueryService.invoiceByOrder(orderNo), request);
    }

    @Operation(summary = "查询当前生效 SOP")
    @GetMapping("/sops/{scenarioType}")
    public ApiResponse<BusinessSnapshotVO<SopRecord>> sop(
            @PathVariable String scenarioType, HttpServletRequest request) {
        return ok(businessQueryService.sop(scenarioType), request);
    }

    private <T extends BusinessQueryRecord> ApiResponse<BusinessSnapshotVO<T>> ok(
            BusinessSnapshotVO<T> value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(value, RequestIdFilter.getRequestId(request));
    }
}
