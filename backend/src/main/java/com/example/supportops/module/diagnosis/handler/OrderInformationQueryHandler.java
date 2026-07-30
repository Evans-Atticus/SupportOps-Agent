package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** 回答订单事实查询；不把普通查询擅自解释为支付、物流等异常。 */
@Component
public class OrderInformationQueryHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() {
        return ScenarioType.ORDER_INFORMATION_QUERY;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        OrderRecord order = context.orders().get(0);
        String currency = currencySymbol(order.currency());
        String total = amount(order.totalAmount());
        String payable = amount(order.payableAmount());
        String orderStatus = orderStatusLabel(order.orderStatus());
        String paymentStatus = paymentStatusLabel(order.paymentStatus());
        String reply = "已为您查询订单 " + order.orderNo() + "：商品为“" + order.productScope()
                + "”，订单总金额 " + currency + total + "，应付金额 " + currency + payable
                + "，当前订单状态为“" + orderStatus + "”，支付状态为“" + paymentStatus + "”。";
        List<DiagnosisEvidence> evidence = List.of(
                HandlerSupport.evidence("biz_orders", order.id(), "total_amount", "订单总金额",
                        order.totalAmount(), "币种：" + order.currency()),
                HandlerSupport.evidence("biz_orders", order.id(), "payable_amount", "应付金额",
                        order.payableAmount(), "订单号：" + order.orderNo()),
                HandlerSupport.evidence("biz_orders", order.id(), "order_status", "订单状态",
                        orderStatus, "支付状态：" + paymentStatus)
        );
        return new DiagnosisResult("订单信息查询完成", reply,
                "直接依据订单快照回答客户询问的字段，不扩展判断异常场景。",
                reply, 1.0, evidence);
    }

    private String amount(BigDecimal value) {
        return value == null ? "--" : value.stripTrailingZeros().toPlainString();
    }

    private String currencySymbol(String currency) {
        return "CNY".equalsIgnoreCase(currency) ? "¥" : (currency == null ? "" : currency + " ");
    }

    private String orderStatusLabel(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "PENDING_PAYMENT" -> "待支付";
            case "PAID" -> "已支付";
            case "PROCESSING" -> "处理中";
            case "SHIPPED" -> "已发货";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "CLOSED" -> "已关闭";
            default -> "处理中";
        };
    }

    private String paymentStatusLabel(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "UNPAID" -> "未支付";
            case "PAID", "SUCCESS" -> "已支付";
            case "PROCESSING" -> "支付处理中";
            case "FAILED" -> "支付失败";
            case "REFUNDED" -> "已退款";
            case "PARTIALLY_REFUNDED" -> "部分退款";
            default -> "处理中";
        };
    }
}
