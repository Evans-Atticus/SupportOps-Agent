package com.example.supportops.module.ai.fallback;

import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/** 已登记场景的安全模板；AI 失败时仍能交付不新增事实的客服回复。 */
@Service
public class TemplateReplyService {
    private final Map<ScenarioType, String> openings;

    public TemplateReplyService() {
        EnumMap<ScenarioType, String> values = new EnumMap<>(ScenarioType.class);
        values.put(ScenarioType.ORDER_INFORMATION_QUERY, "我们已查询您所选订单的实时信息");
        values.put(ScenarioType.PRODUCT_INFORMATION_QUERY, "我们已检索该商品的规格与兼容性资料");
        values.put(ScenarioType.PRODUCT_USAGE_GUIDANCE, "我们已检索该商品的使用与保养资料");
        values.put(ScenarioType.PRODUCT_TROUBLESHOOTING, "我们已检索该商品的故障排查与售后资料");
        values.put(ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING, "我们已核对支付记录与订单状态");
        values.put(ScenarioType.ORDER_CANCELLED_BUT_CHARGED, "我们已核对订单取消、支付和退款记录");
        values.put(ScenarioType.COUPON_UNAVAILABLE, "我们已核对优惠券使用条件");
        values.put(ScenarioType.MEMBER_BENEFIT_NOT_RECEIVED, "我们已核对会员资格与权益发放记录");
        values.put(ScenarioType.LOGISTICS_TRACKING_QUERY, "我们已查询包裹的最新运输轨迹");
        values.put(ScenarioType.LOGISTICS_STATUS_NOT_SYNCED, "我们已核对平台与承运商物流节点");
        values.put(ScenarioType.API_FREQUENT_FAILURE, "我们已核对近期接口调用记录");
        values.put(ScenarioType.INVOICE_ISSUE_FAILED, "我们已核对订单开票资格与发票字段");
        values.put(ScenarioType.PRODUCT_TRACE_ANOMALY, "我们已核对产品全生命周期溯源事件");
        openings = Map.copyOf(values);
    }

    public String render(ScenarioType scenario, DiagnosisResult result) {
        if (scenario == ScenarioType.ORDER_INFORMATION_QUERY || scenario == ScenarioType.LOGISTICS_TRACKING_QUERY
                || scenario == ScenarioType.PRODUCT_INFORMATION_QUERY
                || scenario == ScenarioType.PRODUCT_USAGE_GUIDANCE
                || scenario == ScenarioType.PRODUCT_TROUBLESHOOTING) {
            return result.customerReply();
        }
        return "您好，理解您遇到的问题。" + openings.getOrDefault(scenario, "我们已完成相关记录核查")
                + "，核查结论为：" + result.conclusion() + "。后续将按既定流程处理，"
                + "请以实际处理状态为准。";
    }
}
