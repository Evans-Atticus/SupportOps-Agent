package com.example.supportops.module.business.integration;

import com.example.supportops.module.business.model.query.ApiCallRecord;
import com.example.supportops.module.business.model.query.CouponRecord;
import com.example.supportops.module.business.model.query.CustomerRecord;
import com.example.supportops.module.business.model.query.InvoiceRecord;
import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.MemberRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.business.model.query.RefundRecord;
import com.example.supportops.module.business.model.query.SopRecord;

import java.util.List;

/**
 * 诊断引擎依赖的业务数据端口。新增 ERP/物流厂商时替换适配器，不修改 Java Handler。
 */
public interface BusinessDataSource {
    List<CustomerRecord> customer(String customerNo);
    List<OrderRecord> order(String orderNo);
    List<PaymentRecord> paymentsByOrder(String orderNo);
    List<RefundRecord> refundsByOrder(String orderNo);
    List<CouponRecord> coupon(String couponCode, String customerNo);
    List<MemberRecord> member(String memberNo);
    List<LogisticsRecord> logistics(String trackingNo);
    List<LogisticsRecord> logisticsByOrder(String orderNo);
    List<ApiCallRecord> apiCalls(String clientCode, String apiName, int limit);
    List<InvoiceRecord> invoicesByOrder(String orderNo);
    List<SopRecord> sop(String scenarioType);
}
