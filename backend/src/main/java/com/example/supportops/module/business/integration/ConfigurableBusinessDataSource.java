package com.example.supportops.module.business.integration;

import com.example.supportops.config.PlatformIntegrationProperties;
import com.example.supportops.infrastructure.integration.PlatformHttpClient;
import com.example.supportops.module.business.dao.BusinessQueryDAO;
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
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

/**
 * 混合数据适配器：某个平台 enabled=true 时读取真实 HTTP API，否则保持本地数据。
 * 这种逐平台切换方式可避免一次性替换所有系统的发布风险。
 */
@Component
public class ConfigurableBusinessDataSource implements BusinessDataSource {
    private final BusinessQueryDAO local;
    private final PlatformHttpClient remote;
    private final PlatformIntegrationProperties properties;

    public ConfigurableBusinessDataSource(BusinessQueryDAO local, PlatformHttpClient remote,
                                          PlatformIntegrationProperties properties) {
        this.local = local;
        this.remote = remote;
        this.properties = properties;
    }

    @Override
    public List<CustomerRecord> customer(String customerNo) {
        if (!properties.getErp().isEnabled()) return local.selectCustomer(customerNo);
        return remote.getList("ERP", properties.getErp(), "/customers/" + segment(customerNo), Map.of(), CustomerRecord.class);
    }

    @Override
    public List<OrderRecord> order(String orderNo) {
        if (!properties.getErp().isEnabled()) return local.selectOrder(orderNo);
        return remote.getList("ERP", properties.getErp(), "/orders/" + segment(orderNo), Map.of(), OrderRecord.class);
    }

    @Override
    public List<PaymentRecord> paymentsByOrder(String orderNo) {
        if (!properties.getErp().isEnabled()) return local.selectPaymentsByOrder(orderNo);
        return remote.getList("ERP", properties.getErp(), "/orders/" + segment(orderNo) + "/payments",
                Map.of(), PaymentRecord.class);
    }

    @Override
    public List<RefundRecord> refundsByOrder(String orderNo) {
        if (!properties.getErp().isEnabled()) return local.selectRefundsByOrder(orderNo);
        return remote.getList("ERP", properties.getErp(), "/orders/" + segment(orderNo) + "/refunds",
                Map.of(), RefundRecord.class);
    }

    @Override
    public List<CouponRecord> coupon(String couponCode, String customerNo) {
        if (!properties.getErp().isEnabled()) return local.selectCoupon(couponCode, customerNo);
        return remote.getList("ERP", properties.getErp(), "/coupons/" + segment(couponCode),
                Map.of("customerNo", customerNo), CouponRecord.class);
    }

    @Override
    public List<MemberRecord> member(String memberNo) {
        if (!properties.getMembership().isEnabled()) return local.selectMember(memberNo);
        return remote.getList("会员平台", properties.getMembership(), "/members/" + segment(memberNo),
                Map.of(), MemberRecord.class);
    }

    @Override
    public List<LogisticsRecord> logistics(String trackingNo) {
        if (!properties.getLogistics().isEnabled()) return local.selectLogistics(trackingNo);
        return remote.getList("物流平台", properties.getLogistics(), "/tracking/" + segment(trackingNo),
                Map.of(), LogisticsRecord.class);
    }

    @Override
    public List<LogisticsRecord> logisticsByOrder(String orderNo) {
        if (!properties.getLogistics().isEnabled()) return local.selectLogisticsByOrder(orderNo);
        return remote.getList("物流平台", properties.getLogistics(), "/orders/" + segment(orderNo) + "/logistics",
                Map.of(), LogisticsRecord.class);
    }

    @Override
    public List<ApiCallRecord> apiCalls(String clientCode, String apiName, int limit) {
        if (!properties.getMonitoring().isEnabled()) return local.selectApiCalls(clientCode, apiName, limit);
        java.util.HashMap<String, String> query = new java.util.HashMap<>();
        query.put("clientCode", clientCode);
        query.put("limit", String.valueOf(limit));
        if (apiName != null) query.put("apiName", apiName);
        return remote.getList("监控平台", properties.getMonitoring(), "/api-calls", query, ApiCallRecord.class);
    }

    @Override
    public List<InvoiceRecord> invoicesByOrder(String orderNo) {
        if (!properties.getErp().isEnabled()) return local.selectInvoicesByOrder(orderNo);
        return remote.getList("ERP", properties.getErp(), "/orders/" + segment(orderNo) + "/invoices",
                Map.of(), InvoiceRecord.class);
    }

    @Override
    public List<SopRecord> sop(String scenarioType) {
        // SOP 是本平台审核过的规则资产，不从外部业务平台覆盖。
        return local.selectSop(scenarioType);
    }

    private String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
