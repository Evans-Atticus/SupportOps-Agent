package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.LogisticsRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.List;

/** 物流状态不同步：比较平台与承运商各自最新的物流节点。 */
@Component
public class LogisticsStatusNotSyncedHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() { return ScenarioType.LOGISTICS_STATUS_NOT_SYNCED; }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        if (context.logistics().isEmpty()) {
            OrderRecord order = context.orders().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("缺少订单与物流记录"));
            String status = order.orderStatus();
            return new DiagnosisResult("订单与物流系统查询完成",
                    "当前订单状态为 " + status + "，物流系统尚未生成运单",
                    "订单进入发货环节后再同步承运商物流节点",
                    "您好，已为您查询订单。当前订单状态为 " + status
                            + "，物流系统暂未生成运单信息；订单发货后会在这里同步物流进度。",
                    0.98,
                    List.of(HandlerSupport.evidence("biz_orders", order.id(), "order_status",
                            "订单状态", status, "订单号：" + order.orderNo())));
        }
        LogisticsRecord local = latest(context, "LOCAL");
        LogisticsRecord carrier = latest(context, "CARRIER");
        // 承运商节点必须更新且状态不同，才能证明平台数据已经过期。
        boolean stale = carrier.eventTime().isAfter(local.eventTime())
                && !carrier.logisticsStatus().equals(local.logisticsStatus());
        return new DiagnosisResult("平台与承运商最新物流节点比较完成",
                stale ? "承运商状态更新，但平台物流状态未同步" : "双方最新物流状态一致",
                stale ? "按运单号触发单运单同步任务，防止全量重放" : "无需补偿",
                stale ? "您好，承运商已更新物流状态，平台展示存在延迟，我们已提交同步处理。" : "您好，当前平台与承运商物流状态一致。",
                stale ? 0.99 : 0.9,
                List.of(
                        HandlerSupport.evidence("logistics_records", local.id(), "logistics_status", "平台物流状态",
                                local.logisticsStatus(), "节点时间：" + local.eventTime()),
                        HandlerSupport.evidence("logistics_records", carrier.id(), "logistics_status", "承运商物流状态",
                                carrier.logisticsStatus(), "节点时间：" + carrier.eventTime())
                ));
    }

    /** 按事件时间选取指定来源的最新节点，缺少任一来源时拒绝给出推测性结论。 */
    private LogisticsRecord latest(DiagnosisContext context, String source) {
        return context.logistics().stream().filter(record -> source.equals(record.sourceType()))
                .max(java.util.Comparator.comparing(LogisticsRecord::eventTime))
                .orElseThrow(() -> new IllegalStateException("缺少 " + source + " 物流记录"));
    }
}
