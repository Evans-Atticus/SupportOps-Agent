package com.example.supportops.module.diagnosis.model;

import com.example.supportops.module.business.model.query.ApiCallRecord;
import com.example.supportops.module.business.model.query.CouponRecord;
import com.example.supportops.module.business.model.query.InvoiceRecord;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.MemberRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.business.model.query.RefundRecord;
import com.example.supportops.module.business.model.query.SopRecord;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.trace.model.TraceModels;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeSnippet;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handler 的只读输入快照。统一上下文让规则层不接触 DAO，从而可用纯单元测试验证。
 */
public record DiagnosisContext(
        ScenarioType scenarioType,
        String ticketNo,
        String businessNo,
        LocalDateTime diagnosedAt,
        List<OrderRecord> orders,
        List<PaymentRecord> payments,
        List<RefundRecord> refunds,
        List<CouponRecord> coupons,
        List<MemberRecord> members,
        List<LogisticsRecord> logistics,
        List<ApiCallRecord> apiCalls,
        List<InvoiceRecord> invoices,
        List<ProductKnowledgeSnippet> productKnowledge,
        SopRecord sop,
        TraceModels.TraceDetail traceDetail,
        String customerQuestion
) {
    /** 保留原构造签名，现有七个场景及其单元测试无需了解新增溯源字段。 */
    public DiagnosisContext(ScenarioType scenarioType, String ticketNo, String businessNo, LocalDateTime diagnosedAt,
                            List<OrderRecord> orders, List<PaymentRecord> payments, List<RefundRecord> refunds,
                            List<CouponRecord> coupons, List<MemberRecord> members, List<LogisticsRecord> logistics,
                            List<ApiCallRecord> apiCalls, List<InvoiceRecord> invoices, SopRecord sop) {
        this(scenarioType, ticketNo, businessNo, diagnosedAt, orders, payments, refunds, coupons, members,
                logistics, apiCalls, invoices, List.of(), sop, null, "");
    }

    public DiagnosisContext(ScenarioType scenarioType, String ticketNo, String businessNo, LocalDateTime diagnosedAt,
                            List<OrderRecord> orders, List<PaymentRecord> payments, List<RefundRecord> refunds,
                            List<CouponRecord> coupons, List<MemberRecord> members, List<LogisticsRecord> logistics,
                            List<ApiCallRecord> apiCalls, List<InvoiceRecord> invoices, SopRecord sop,
                            TraceModels.TraceDetail traceDetail) {
        this(scenarioType, ticketNo, businessNo, diagnosedAt, orders, payments, refunds, coupons, members,
                logistics, apiCalls, invoices, List.of(), sop, traceDetail, "");
    }

    /** 保留包含产品知识与溯源数据的原构造签名。 */
    public DiagnosisContext(ScenarioType scenarioType, String ticketNo, String businessNo, LocalDateTime diagnosedAt,
                            List<OrderRecord> orders, List<PaymentRecord> payments, List<RefundRecord> refunds,
                            List<CouponRecord> coupons, List<MemberRecord> members, List<LogisticsRecord> logistics,
                            List<ApiCallRecord> apiCalls, List<InvoiceRecord> invoices,
                            List<ProductKnowledgeSnippet> productKnowledge, SopRecord sop,
                            TraceModels.TraceDetail traceDetail) {
        this(scenarioType, ticketNo, businessNo, diagnosedAt, orders, payments, refunds, coupons, members,
                logistics, apiCalls, invoices, productKnowledge, sop, traceDetail, "");
    }
    public DiagnosisContext {
        // record 本身字段不可重新赋值，但传入的 List 可能可变，因此在边界处进行防御性复制。
        orders = immutable(orders);
        payments = immutable(payments);
        refunds = immutable(refunds);
        coupons = immutable(coupons);
        members = immutable(members);
        logistics = immutable(logistics);
        apiCalls = immutable(apiCalls);
        invoices = immutable(invoices);
        productKnowledge = immutable(productKnowledge);
        customerQuestion = customerQuestion == null ? "" : customerQuestion.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        // 把未查询的场景数据统一表示为空列表，Handler 无需反复判断 null。
        return values == null ? List.of() : List.copyOf(values);
    }
}
