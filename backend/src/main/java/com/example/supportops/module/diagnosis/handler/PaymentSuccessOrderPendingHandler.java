package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.List;

/** 支付成功但订单仍待支付：核对支付结果、订单状态和支付回调三组事实。 */
@Component
public class PaymentSuccessOrderPendingHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() {
        return ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        OrderRecord order = context.orders().get(0);
        PaymentRecord payment = context.payments().get(0);
        // 常量放在 equals 左侧，即使数据库字段为 null 也不会触发空指针。
        boolean paid = "SUCCESS".equals(payment.paymentStatus());
        boolean orderPending = "PENDING_PAYMENT".equals(order.orderStatus()) || "UNPAID".equals(order.paymentStatus());
        boolean callbackFailed = "FAILED".equals(payment.callbackStatus());

        List<DiagnosisEvidence> evidence = List.of(
                HandlerSupport.evidence("payment_records", payment.id(), "payment_status", "支付状态",
                        payment.paymentStatus(), "支付渠道已确认交易结果"),
                HandlerSupport.evidence("payment_records", payment.id(), "callback_status", "回调状态",
                        payment.callbackStatus(), "回调错误码：" + payment.callbackErrorCode()),
                HandlerSupport.evidence("biz_orders", order.id(), "order_status", "订单状态",
                        order.orderStatus(), "订单支付状态：" + order.paymentStatus())
        );
        // 三个条件同时成立才能定位为回调同步故障，避免仅凭“订单待支付”误判。
        String conclusion = paid && orderPending && callbackFailed
                ? "支付回调失败导致订单状态未同步"
                : "当前数据不满足“支付成功但订单未更新”的完整异常条件";
        return new DiagnosisResult("支付记录与订单状态核对完成", conclusion,
                callbackFailed ? "提交带订单号的幂等状态补偿任务，并监控回调重试结果" : "转人工复核支付渠道原始流水",
                "您好，经核查支付记录已确认，我们正在按流程处理订单状态同步问题，请勿重复支付。",
                paid && orderPending && callbackFailed ? 0.98 : 0.65, evidence);
    }
}
