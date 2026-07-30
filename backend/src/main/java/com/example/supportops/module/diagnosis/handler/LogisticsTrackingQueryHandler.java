package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 客户物流查询：返回路线、当前位置、最新节点和预计送达，不把同步差异当成客户答案。 */
@Component
public class LogisticsTrackingQueryHandler implements ScenarioDiagnosisHandler {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    @Override
    public ScenarioType supports() {
        return ScenarioType.LOGISTICS_TRACKING_QUERY;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        if (context.logistics().isEmpty()) {
            OrderRecord order = context.orders().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("缺少订单与物流记录"));
            return diagnoseOrderWithoutWaybill(order);
        }

        List<LogisticsRecord> carrierEvents = context.logistics().stream()
                .filter(item -> "CARRIER".equals(item.sourceType()))
                .sorted(Comparator.comparing(LogisticsRecord::eventTime).reversed())
                .toList();
        List<LogisticsRecord> events = carrierEvents.isEmpty()
                ? context.logistics().stream().sorted(Comparator.comparing(LogisticsRecord::eventTime).reversed()).toList()
                : carrierEvents;
        LogisticsRecord latest = events.get(0);
        String origin = value(latest.originLocation(), "发件地待承运商回传");
        String destination = value(latest.destinationLocation(), "收件地待承运商回传");
        String current = value(latest.currentLocation(), value(latest.facilityName(), "当前位置待更新"));
        String carrier = value(latest.carrierName(), "承运商");
        String eta = latest.estimatedDeliveryAt() == null ? "预计送达时间待承运商更新"
                : "预计 " + TIME.format(latest.estimatedDeliveryAt()) + " 前送达";
        String courier = latest.courierNameMasked() == null ? ""
                : "，派送员 " + latest.courierNameMasked()
                + (latest.courierPhoneMasked() == null ? "" : "（" + latest.courierPhoneMasked() + "）");
        String latestProgress = value(latest.statusDescription(), status(latest.logisticsStatus()));
        String reply = "您的包裹由" + carrier + "承运，已从" + origin + "发出，目的地为" + destination
                + "。最新进度：" + TIME.format(latest.eventTime()) + "，包裹位于" + current + "，"
                + latestProgress + courier + "。" + eta + "。";

        List<DiagnosisEvidence> evidence = new ArrayList<>();
        evidence.add(HandlerSupport.evidence("logistics_records", latest.id(), "current_location",
                "当前位置", current, "运单号：" + latest.trackingNo()));
        evidence.add(HandlerSupport.evidence("logistics_records", latest.id(), "logistics_status",
                "最新物流状态", status(latest.logisticsStatus()), "节点时间：" + TIME.format(latest.eventTime())));
        if (latest.estimatedDeliveryAt() != null) {
            evidence.add(HandlerSupport.evidence("logistics_records", latest.id(), "estimated_delivery_at",
                    "预计送达", TIME.format(latest.estimatedDeliveryAt()), origin + " → " + destination));
        }
        return new DiagnosisResult("物流路线与最新节点查询完成",
                origin + " → " + destination + "，当前位于" + current,
                status(latest.logisticsStatus()), reply, 0.99, evidence);
    }

    /**
     * 没有运单不是最终结论。先回查订单状态，解释订单为何尚未或不会进入物流流程。
     */
    private DiagnosisResult diagnoseOrderWithoutWaybill(OrderRecord order) {
        String orderStatus = value(order.orderStatus(), "UNKNOWN").toUpperCase();
        String paymentStatus = value(order.paymentStatus(), "UNKNOWN").toUpperCase();
        String conclusion;
        String suggestion;
        String reply;

        if ("CANCELLED".equals(orderStatus) || "CLOSED".equals(orderStatus)) {
            conclusion = "订单" + orderStatusLabel(orderStatus) + "，不会生成运单";
            suggestion = "订单已终止，无需继续等待物流；若存在实际扣款，应继续核对退款进度";
            reply = "已为您查询订单 " + order.orderNo() + "，当前订单状态为“"
                    + orderStatusLabel(orderStatus) + "”，交易已终止，因此不会进入仓库出库和配送流程，也不会生成运单。"
                    + ("PAID".equals(paymentStatus) || "SUCCESS".equals(paymentStatus)
                    ? "系统显示该订单已支付；如款项确实已扣除，可以继续为您查询退款进度。"
                    : "该订单当前无需等待物流更新。");
        } else if ("PENDING_PAYMENT".equals(orderStatus) || "UNPAID".equals(paymentStatus)) {
            conclusion = "订单待支付，尚未进入履约流程";
            suggestion = "提示客户先完成支付；未支付订单不会安排出库或生成运单";
            reply = "已为您查询订单 " + order.orderNo()
                    + "，当前订单状态为“待支付”。订单尚未完成购买和支付，因此商家还不会安排出库，也不会生成运单。"
                    + "如仍需要该商品，请先完成支付；支付成功并发货后即可查询物流轨迹。";
        } else if ("PAID".equals(orderStatus) || "PROCESSING".equals(orderStatus)) {
            conclusion = "订单已支付，正在等待商家发货";
            suggestion = "订单已经进入履约流程，等待仓库出库和承运商揽收";
            reply = "已为您查询订单 " + order.orderNo() + "，当前订单状态为“"
                    + orderStatusLabel(orderStatus) + "”。订单已经进入处理流程，但商家尚未发货，所以暂时没有运单。"
                    + "仓库出库并由承运商揽收后，系统会显示物流路线和预计送达时间。";
        } else if ("SHIPPED".equals(orderStatus)) {
            conclusion = "订单已发货，但运单信息尚未同步";
            suggestion = "订单状态与物流数据不一致，应核对仓库出库记录和承运商回传";
            reply = "已为您查询订单 " + order.orderNo()
                    + "，订单状态显示“已发货”，但系统暂未收到对应运单和物流节点。"
                    + "这属于发货信息尚未同步，我已保留该状态供客服进一步核对。";
        } else {
            conclusion = "订单状态为" + orderStatusLabel(orderStatus) + "，暂无可用物流轨迹";
            suggestion = "根据订单状态继续核对履约方式，不推测未返回的物流信息";
            reply = "已为您查询订单 " + order.orderNo() + "，当前订单状态为“"
                    + orderStatusLabel(orderStatus) + "”。该订单目前没有可查询的运单记录，"
                    + "系统将以订单的实际履约状态为准，不会虚构物流位置。";
        }

        List<DiagnosisEvidence> evidence = List.of(
                HandlerSupport.evidence("biz_orders", order.id(), "order_status",
                        "订单状态", orderStatusLabel(orderStatus), "订单号：" + order.orderNo()),
                HandlerSupport.evidence("biz_orders", order.id(), "payment_status",
                        "支付状态", paymentStatusLabel(paymentStatus), "订单号：" + order.orderNo())
        );
        return new DiagnosisResult("订单状态核验完成", conclusion, suggestion, reply, 1.0, evidence);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String status(String value) {
        return switch (value == null ? "" : value) {
            case "PICKED_UP" -> "已揽收";
            case "IN_TRANSIT" -> "运输中";
            case "ARRIVED_TRANSIT" -> "已到达转运中心";
            case "OUT_FOR_DELIVERY" -> "派送中";
            case "DELIVERED" -> "已签收";
            case "EXCEPTION" -> "运输异常";
            default -> value == null ? "状态待更新" : value;
        };
    }

    private String orderStatusLabel(String status) {
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
        return switch (status) {
            case "UNPAID" -> "未支付";
            case "PAID", "SUCCESS" -> "已支付";
            case "PROCESSING" -> "支付处理中";
            case "FAILED" -> "支付失败";
            case "REFUNDED" -> "已退款";
            case "PARTIALLY_REFUNDED" -> "部分退款";
            default -> "未知";
        };
    }
}
