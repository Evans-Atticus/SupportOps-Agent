package com.example.supportops.module.business.service;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.business.integration.BusinessDataSource;
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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusinessQueryService {
    private final BusinessDataSource businessDataSource;

    public BusinessQueryService(BusinessDataSource businessDataSource) {
        this.businessDataSource = businessDataSource;
    }

    public BusinessSnapshotVO<CustomerRecord> customer(String customerNo) {
        return required("customer", customerNo, businessDataSource.customer(customerNo));
    }

    public BusinessSnapshotVO<OrderRecord> order(String orderNo) {
        return required("order", orderNo, businessDataSource.order(orderNo));
    }

    public BusinessSnapshotVO<PaymentRecord> paymentsByOrder(String orderNo) {
        return required("payment", orderNo, businessDataSource.paymentsByOrder(orderNo));
    }

    public BusinessSnapshotVO<RefundRecord> refundsByOrder(String orderNo) {
        return required("refund", orderNo, businessDataSource.refundsByOrder(orderNo));
    }

    public BusinessSnapshotVO<CouponRecord> coupon(String couponCode, String customerNo) {
        return required("coupon", couponCode + ":" + customerNo,
                businessDataSource.coupon(couponCode, customerNo));
    }

    public BusinessSnapshotVO<MemberRecord> member(String memberNo) {
        return required("member", memberNo, businessDataSource.member(memberNo));
    }

    public BusinessSnapshotVO<LogisticsRecord> logistics(String trackingNo) {
        return required("logistics", trackingNo, businessDataSource.logistics(trackingNo));
    }

    public BusinessSnapshotVO<LogisticsRecord> logisticsByOrder(String orderNo) {
        return required("logistics", orderNo, businessDataSource.logisticsByOrder(orderNo));
    }

    public BusinessSnapshotVO<ApiCallRecord> apiCalls(String clientCode, String apiName, int limit) {
        return required("apiCall", clientCode, businessDataSource.apiCalls(clientCode, apiName, limit));
    }

    public BusinessSnapshotVO<InvoiceRecord> invoiceByOrder(String orderNo) {
        return required("invoice", orderNo, businessDataSource.invoicesByOrder(orderNo));
    }

    public BusinessSnapshotVO<SopRecord> sop(String scenarioType) {
        return required("sop", scenarioType, businessDataSource.sop(scenarioType));
    }

    private <T extends BusinessQueryRecord> BusinessSnapshotVO<T> required(
            String category,
            String businessKey,
            List<T> records
    ) {
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    category + " 记录不存在: " + businessKey);
        }
        return new BusinessSnapshotVO<>(category, businessKey, List.copyOf(records));
    }
}
