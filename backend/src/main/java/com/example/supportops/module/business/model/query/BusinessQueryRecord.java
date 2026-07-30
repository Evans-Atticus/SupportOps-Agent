package com.example.supportops.module.business.model.query;

public sealed interface BusinessQueryRecord permits CustomerRecord, OrderRecord, PaymentRecord, RefundRecord,
        CouponRecord, MemberRecord, LogisticsRecord, ApiCallRecord, InvoiceRecord, SopRecord {
}
