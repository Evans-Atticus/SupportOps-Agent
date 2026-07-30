package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.PaymentRecord;
import com.example.supportops.module.business.model.query.RefundRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.List;

/** 订单取消仍扣款：检查取消事实、成功支付事实以及退款的最终状态。 */
@Component
public class OrderCancelledButChargedHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() { return ScenarioType.ORDER_CANCELLED_BUT_CHARGED; }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        OrderRecord order = context.orders().get(0);
        PaymentRecord payment = context.payments().get(0);
        RefundRecord refund = context.refunds().get(0);
        // 退款处于 PENDING/FAILED 等非成功状态时，才能认定取消后的资金链路尚未闭环。
        boolean abnormal = "CANCELLED".equals(order.orderStatus())
                && "SUCCESS".equals(payment.paymentStatus())
                && !"SUCCESS".equals(refund.refundStatus());
        return new DiagnosisResult("取消、扣款与退款链路核对完成",
                abnormal ? "订单取消后退款任务仍未完成" : "退款链路当前未发现确定性异常",
                abnormal ? "按退款号幂等补发退款任务并由财务队列跟踪" : "继续观察退款最终状态",
                "您好，订单已取消，当前退款记录仍在处理流程中，我们已提交核查；实际到账时间以支付渠道为准。",
                abnormal ? 0.97 : 0.65,
                List.of(
                        HandlerSupport.evidence("biz_orders", order.id(), "order_status", "订单状态", order.orderStatus(), "订单已取消"),
                        HandlerSupport.evidence("payment_records", payment.id(), "payment_status", "支付状态", payment.paymentStatus(), "扣款记录成功"),
                        HandlerSupport.evidence("refund_records", refund.id(), "refund_status", "退款状态", refund.refundStatus(), "失败码：" + refund.failureCode())
                ));
    }
}
