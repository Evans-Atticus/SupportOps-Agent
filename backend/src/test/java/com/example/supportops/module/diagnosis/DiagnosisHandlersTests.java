package com.example.supportops.module.diagnosis;

import com.example.supportops.module.business.model.query.*;
import com.example.supportops.module.diagnosis.handler.*;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisHandlersTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Test
    void paymentCallbackFailureIsDiagnosed() {
        DiagnosisContext context = base(ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING,
                List.of(order(1L, "PENDING_PAYMENT", "UNPAID", new BigDecimal("299"), null)),
                List.of(new PaymentRecord(1L, "P1", "O1", "ALIPAY", "SUCCESS", new BigDecimal("299"),
                        "FAILED", "ORDER_SERVICE_TIMEOUT", 3, NOW.minusMinutes(5), NOW.minusMinutes(2))),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertDiagnosis(new PaymentSuccessOrderPendingHandler().diagnose(context), "回调失败");
    }

    @Test
    void orderAmountQueryReturnsStoredFacts() {
        DiagnosisContext context = base(ScenarioType.ORDER_INFORMATION_QUERY,
                List.of(order(1L, "COMPLETED", "PAID", new BigDecimal("368.00"), null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        DiagnosisResult result = new OrderInformationQueryHandler().diagnose(context);

        assertTrue(result.customerReply().contains("¥368"), result.customerReply());
        assertTrue(result.customerReply().contains("已支付"), result.customerReply());
        assertFalse(result.evidences().isEmpty());
    }

    @Test
    void cancelledOrderPendingRefundIsDiagnosed() {
        DiagnosisContext context = base(ScenarioType.ORDER_CANCELLED_BUT_CHARGED,
                List.of(order(2L, "CANCELLED", "PAID", new BigDecimal("128"), null)),
                List.of(new PaymentRecord(2L, "P2", "O1", "WECHAT", "SUCCESS", new BigDecimal("128"),
                        "SUCCESS", null, 1, NOW, NOW)),
                List.of(new RefundRecord(1L, "R1", "O1", "PENDING", new BigDecimal("128"),
                        "REFUND_JOB_NOT_TRIGGERED", NOW, null)), List.of(), List.of(), List.of(), List.of(), List.of());
        assertDiagnosis(new OrderCancelledButChargedHandler().diagnose(context), "退款任务仍未完成");
    }

    @Test
    void couponThresholdIsDiagnosed() {
        CouponRecord coupon = new CouponRecord(1L, "C1", "满减券", "ACTIVE", new BigDecimal("300"),
                new BigDecimal("50"), "ALL", NOW.minusDays(1), NOW.plusDays(1), "AVAILABLE", NOW.minusDays(1), "CU1");
        DiagnosisContext context = base(ScenarioType.COUPON_UNAVAILABLE,
                List.of(order(3L, "PENDING_PAYMENT", "UNPAID", new BigDecimal("199"), "C1")),
                List.of(), List.of(), List.of(coupon), List.of(), List.of(), List.of(), List.of());
        assertDiagnosis(new CouponUnavailableHandler().diagnose(context), "未达到使用门槛");
    }

    @Test
    void memberGrantFailureIsDiagnosed() {
        MemberRecord member = new MemberRecord(1L, "M1", "CU1", "GOLD", "ACTIVE", NOW.minusDays(1),
                NOW.plusYears(1), "B1", "SHIP", "运费券", "FAILED", "EVENT_LOST", NOW.minusHours(1), null);
        DiagnosisContext context = base(ScenarioType.MEMBER_BENEFIT_NOT_RECEIVED,
                List.of(), List.of(), List.of(), List.of(), List.of(member), List.of(), List.of(), List.of());
        assertDiagnosis(new MemberBenefitNotReceivedHandler().diagnose(context), "权益发放事件失败");
    }

    @Test
    void logisticsMismatchIsDiagnosed() {
        List<LogisticsRecord> records = List.of(
                new LogisticsRecord(1L, "SF1", "O1", "LOCAL", "IN_TRANSIT", "运输中", NOW.minusHours(2), NOW),
                new LogisticsRecord(2L, "SF1", "O1", "CARRIER", "DELIVERED", "已签收", NOW, null));
        DiagnosisContext context = base(ScenarioType.LOGISTICS_STATUS_NOT_SYNCED,
                List.of(), List.of(), List.of(), List.of(), List.of(), records, List.of(), List.of());
        assertDiagnosis(new LogisticsStatusNotSyncedHandler().diagnose(context), "未同步");
    }

    @Test
    void logisticsTrackingReturnsRouteCurrentAreaAndEta() {
        LogisticsRecord event = new LogisticsRecord(3L, "SF1", "O1", "CARRIER", "顺丰速运",
                "OUT_FOR_DELIVERY", "快递员正在派送", "上海市浦东新区", "江苏省苏州市工业园区",
                "江苏省苏州市工业园区金鸡湖街道", "顺丰苏州工业园区营业点",
                "李师傅", "138****6208", NOW.plusHours(5), NOW, null);
        DiagnosisContext context = base(ScenarioType.LOGISTICS_TRACKING_QUERY,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(event), List.of(), List.of());

        DiagnosisResult result = new LogisticsTrackingQueryHandler().diagnose(context);

        assertTrue(result.customerReply().contains("上海市浦东新区"), result.customerReply());
        assertTrue(result.customerReply().contains("金鸡湖街道"), result.customerReply());
        assertTrue(result.customerReply().contains("17:00"), result.customerReply());
    }

    @Test
    void logisticsQuestionExplainsCancelledOrderInsteadOfWaitingForWaybill() {
        OrderRecord order = new OrderRecord(1L, "O1", "C1", "CANCELLED", "UNPAID",
                new BigDecimal("100.00"), new BigDecimal("100.00"), "CNY", null,
                "测试商品", null, null, NOW);
        DiagnosisContext context = base(ScenarioType.LOGISTICS_TRACKING_QUERY,
                List.of(order), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        DiagnosisResult result = new LogisticsTrackingQueryHandler().diagnose(context);

        assertTrue(result.customerReply().contains("当前订单状态为“已取消”"), result.customerReply());
        assertTrue(result.customerReply().contains("不会生成运单"), result.customerReply());
        assertFalse(result.customerReply().contains("退款"), result.customerReply());
    }

    @Test
    void apiFailureRateIsDiagnosed() {
        List<ApiCallRecord> calls = List.of(
                api(1L, "SUCCESS", 200, null, NOW.minusMinutes(8)),
                api(2L, "FAILED", 503, "TIMEOUT", NOW.minusMinutes(5)),
                api(3L, "FAILED", 503, "CIRCUIT_OPEN", NOW));
        DiagnosisContext context = base(ScenarioType.API_FREQUENT_FAILURE,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), calls, List.of());
        assertDiagnosis(new ApiFrequentFailureHandler().diagnose(context), "达到告警阈值");
    }

    @Test
    void missingCompanyTaxNumberIsDiagnosed() {
        InvoiceRecord invoice = new InvoiceRecord(1L, "I1", "O1", "COMPANY", "示例公司", null,
                "a***@example.com", "VERIFIED", "FAILED", "MISSING_TAX_NO", null);
        DiagnosisContext context = base(ScenarioType.INVOICE_ISSUE_FAILED,
                List.of(order(7L, "COMPLETED", "PAID", new BigDecimal("888"), null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(invoice));
        assertDiagnosis(new InvoiceIssueFailedHandler().diagnose(context), "企业税号");
    }

    @Test
    void unsignedOrderWaitsForReceiptWithoutInvoiceApplication() {
        DiagnosisContext context = base(ScenarioType.INVOICE_ISSUE_FAILED,
                List.of(order(1L, "SHIPPED", "PAID", new BigDecimal("299"), null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        DiagnosisResult result = new InvoiceIssueFailedHandler().diagnose(context);

        assertTrue(result.conclusion().contains("WAITING_RECEIPT"), result.conclusion());
        assertEquals("当前订单尚未签收，暂不具备开票条件。订单签收后，系统将继续处理您的发票申请。",
                result.customerReply());
    }

    @Test
    void issuedInvoiceReturnsIssuedConsultationStatus() {
        InvoiceRecord invoice = new InvoiceRecord(2L, "I2", "O1", "PERSONAL", "个人", null,
                "a***@example.com", "VERIFIED", "ISSUED", null, NOW);
        DiagnosisContext context = base(ScenarioType.INVOICE_ISSUE_FAILED,
                List.of(order(2L, "COMPLETED", "PAID", new BigDecimal("299"), null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(invoice));

        DiagnosisResult result = new InvoiceIssueFailedHandler().diagnose(context);

        assertTrue(result.conclusion().contains("ISSUED"), result.conclusion());
        assertTrue(result.customerReply().contains("已开具成功"), result.customerReply());
    }

    private void assertDiagnosis(DiagnosisResult result, String expected) {
        assertTrue(result.conclusion().contains(expected), result.conclusion());
        assertFalse(result.evidences().isEmpty());
    }

    private DiagnosisContext base(ScenarioType type, List<OrderRecord> orders, List<PaymentRecord> payments,
                                  List<RefundRecord> refunds, List<CouponRecord> coupons, List<MemberRecord> members,
                                  List<LogisticsRecord> logistics, List<ApiCallRecord> apiCalls, List<InvoiceRecord> invoices) {
        return new DiagnosisContext(type, "TK1", "O1", NOW, orders, payments, refunds, coupons, members,
                logistics, apiCalls, invoices, null);
    }

    private OrderRecord order(Long id, String orderStatus, String paymentStatus, BigDecimal amount, String coupon) {
        return new OrderRecord(id, "O1", "CU1", orderStatus, paymentStatus, amount, amount, "CNY", coupon,
                "ALL", NOW, null, NOW);
    }

    private ApiCallRecord api(Long id, String status, int httpStatus, String errorCode, LocalDateTime calledAt) {
        return new ApiCallRecord(id, "CLIENT1", "queryOrder", "T" + id, status, httpStatus, errorCode, 10, calledAt);
    }
}
