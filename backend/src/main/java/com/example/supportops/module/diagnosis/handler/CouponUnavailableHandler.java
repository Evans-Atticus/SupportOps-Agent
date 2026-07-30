package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.CouponRecord;
import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 优惠券不可用：逐项校验券状态、有效期、金额门槛和商品范围。 */
@Component
public class CouponUnavailableHandler implements ScenarioDiagnosisHandler {
    @Override
    public ScenarioType supports() { return ScenarioType.COUPON_UNAVAILABLE; }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        OrderRecord order = context.orders().get(0);
        CouponRecord coupon = context.coupons().get(0);
        // 收集全部未满足条件，一次性向客服解释，避免客户逐次修改后再次失败。
        List<String> reasons = new ArrayList<>();
        // 券模板和客户领取记录都必须处于可用状态。
        if (!"ACTIVE".equals(coupon.couponStatus()) || !"AVAILABLE".equals(coupon.receiveStatus())) reasons.add("优惠券状态不可用");
        // 诊断时间早于生效时间或晚于失效时间，均视为不在有效期。
        if (context.diagnosedAt().isBefore(coupon.validFrom()) || context.diagnosedAt().isAfter(coupon.validUntil())) reasons.add("不在有效期内");
        // BigDecimal 使用 compareTo 比较金额，不能用 equals（equals 还会比较小数位数）。
        if (order.payableAmount().compareTo(coupon.thresholdAmount()) < 0) reasons.add("订单金额未达到使用门槛");
        // ALL 代表不限制品类；否则券范围必须与订单商品范围一致。
        if (!"ALL".equals(coupon.productScope()) && !coupon.productScope().equals(order.productScope())) reasons.add("商品范围不匹配");
        String conclusion = reasons.isEmpty() ? "优惠券规则均满足，需检查营销计算服务" : String.join("；", reasons);
        return new DiagnosisResult("优惠券归属、状态、有效期、门槛和范围校验完成", conclusion,
                "向客户说明命中的确定性规则；若规则均满足则转营销平台排查",
                "您好，优惠券未能使用的原因是：" + conclusion + "。您可调整订单后重试。",
                reasons.isEmpty() ? 0.72 : 0.99,
                List.of(
                        HandlerSupport.evidence("coupons", coupon.id(), "coupon_status", "券状态", coupon.couponStatus(), "领取状态：" + coupon.receiveStatus()),
                        HandlerSupport.evidence("coupons", coupon.id(), "valid_until", "有效期至", coupon.validUntil(), "诊断时间：" + context.diagnosedAt()),
                        HandlerSupport.evidence("biz_orders", order.id(), "payable_amount", "订单应付金额", order.payableAmount(), "使用门槛：" + coupon.thresholdAmount()),
                        HandlerSupport.evidence("biz_orders", order.id(), "product_scope", "商品范围", order.productScope(), "券适用范围：" + coupon.productScope())
                ));
    }
}
