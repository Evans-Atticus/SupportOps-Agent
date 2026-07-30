package com.example.supportops.module.ai.fallback;

import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** 模型理解失败时的演示兜底；只返回场景，不替代 Java 根因规则。 */
@Component
public class KeywordFallbackClassifier {
    public ScenarioType classify(String description, String trustedHint) {
        return classifyAll(description, trustedHint).stream().findFirst().orElse(ScenarioType.UNKNOWN);
    }

    /** 模型不可用时仍要保留客户同一句话中的所有明确问题，并按其在原文中的出现顺序执行。 */
    public List<ScenarioType> classifyAll(String description, String trustedHint) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        List<Match> matches = new ArrayList<>();

        boolean paymentPending = text.contains("支付") && (text.contains("待支付") || text.contains("未更新"))
                && (text.contains("成功") || text.contains("扣款") || text.contains("已付"));
        if (paymentPending) add(matches, ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING, text,
                "支付", "扣款", "待支付");
        if (text.contains("取消") && (text.contains("扣款") || text.contains("退款")))
            add(matches, ScenarioType.ORDER_CANCELLED_BUT_CHARGED, text, "取消", "退款", "扣款");
        if (text.contains("优惠券")) add(matches, ScenarioType.COUPON_UNAVAILABLE, text, "优惠券");
        if (text.contains("会员") || text.contains("权益"))
            add(matches, ScenarioType.MEMBER_BENEFIT_NOT_RECEIVED, text, "会员", "权益");
        boolean logisticsContext = text.contains("物流") || text.contains("快递") || text.contains("包裹")
                || text.contains("运单") || text.contains("派送") || text.contains("签收");
        boolean logisticsMismatch = text.contains("未同步") || text.contains("不同步")
                || text.contains("没有同步") || text.contains("没同步")
                || text.contains("未更新") || text.contains("不更新") || text.contains("显示不一致");
        if (logisticsContext && logisticsMismatch)
            add(matches, ScenarioType.LOGISTICS_STATUS_NOT_SYNCED, text, "物流", "快递", "包裹", "运单");
        else if (logisticsContext)
            add(matches, ScenarioType.LOGISTICS_TRACKING_QUERY, text, "物流", "快递", "包裹", "运单", "派送", "签收");
        if (text.contains("api") || text.contains("接口") || text.contains("503"))
            add(matches, ScenarioType.API_FREQUENT_FAILURE, text, "api", "接口", "503");
        if (text.contains("发票") || text.contains("开票"))
            add(matches, ScenarioType.INVOICE_ISSUE_FAILED, text, "发票", "开票");
        if (text.contains("溯源") || text.contains("批次") || text.contains("质检")
                || text.contains("仓库") || text.contains("入库") || text.contains("出库") || text.contains("召回"))
            add(matches, ScenarioType.PRODUCT_TRACE_ANOMALY, text,
                    "溯源", "批次", "质检", "仓库", "入库", "出库", "召回");

        boolean productTrouble = containsAny(text, "故障", "坏了", "没反应", "无法开机", "不能开机",
                "异响", "发热", "过热", "烫手", "异味", "焦味", "鼓包", "膨胀", "冒烟", "起火",
                "爆炸", "爆裂", "漏液", "漏电", "触电", "进水", "短路", "火花", "死机", "报错",
                "维修", "排障", "排查");
        boolean productUsage = containsAny(text, "怎么用", "如何使用", "使用方法", "说明书", "怎么安装",
                "如何安装", "怎么连接", "如何连接", "配对", "保养", "清洁", "充电方法", "操作步骤");
        boolean productFacts = containsAny(text, "规格", "参数", "尺寸", "重量", "材质", "颜色", "型号",
                "兼容", "适配", "支持什么", "防水", "续航", "功率", "容量", "功能", "保修期");
        if (productTrouble) add(matches, ScenarioType.PRODUCT_TROUBLESHOOTING, text,
                "故障", "坏了", "没反应", "无法开机", "不能开机", "异响", "发热", "过热", "烫手",
                "异味", "焦味", "鼓包", "膨胀", "冒烟", "起火", "爆炸", "爆裂", "漏液", "漏电",
                "触电", "进水", "短路", "火花", "死机", "报错", "维修", "排障", "排查");
        if (productUsage) add(matches, ScenarioType.PRODUCT_USAGE_GUIDANCE, text,
                "怎么用", "如何使用", "使用方法", "说明书", "安装", "连接", "配对", "保养", "清洁", "操作步骤");
        if (productFacts) add(matches, ScenarioType.PRODUCT_INFORMATION_QUERY, text,
                "规格", "参数", "尺寸", "重量", "材质", "颜色", "型号", "兼容", "适配", "支持什么", "防水", "续航", "功率", "容量", "功能", "保修期");

        boolean orderContext = text.contains("订单") || text.contains("商品") || text.contains("产品");
        boolean monetaryFact = text.contains("金额") || text.contains("价格") || text.contains("多少钱")
                || text.contains("应付") || text.contains("实付");
        boolean factField = monetaryFact || text.contains("支付状态")
                || text.contains("订单状态") || text.contains("订单信息") || text.contains("订单详情");
        // 专门的支付异常处理已包含状态核验；只有同时询问金额等独立事实时才追加订单查询。
        if ((monetaryFact || (orderContext && factField) || text.contains("查询金额") || text.contains("查询订单"))
                && (!paymentPending || monetaryFact)) {
            add(matches, ScenarioType.ORDER_INFORMATION_QUERY, text,
                    "金额", "价格", "多少钱", "应付", "实付", "订单状态", "订单信息", "订单详情", "查询订单");
        }

        if (!matches.isEmpty()) {
            matches.sort(java.util.Comparator.comparingInt(Match::position));
            LinkedHashSet<ScenarioType> ordered = new LinkedHashSet<>();
            matches.forEach(match -> ordered.add(match.scenario()));
            return List.copyOf(ordered);
        }

        // 历史工单 hint 只在本轮提问没有表达明确意图时兜底，不能覆盖客户当前问题。
        if (trustedHint != null) {
            try {
                ScenarioType hinted = ScenarioType.valueOf(trustedHint);
                if (hinted != ScenarioType.UNKNOWN) return List.of(hinted);
            } catch (IllegalArgumentException ignored) {
                // 非法 hint 不让脏数据中断降级链路。
            }
        }
        return List.of(ScenarioType.UNKNOWN);
    }

    private void add(List<Match> matches, ScenarioType scenario, String text, String... keywords) {
        int position = Integer.MAX_VALUE;
        for (String keyword : keywords) {
            int found = text.indexOf(keyword);
            if (found >= 0) position = Math.min(position, found);
        }
        matches.add(new Match(scenario, position));
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private record Match(ScenarioType scenario, int position) {}
}
