package com.example.supportops.module.diagnosis.plan;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.diagnosis.model.DiagnosisPlan;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 查询计划白名单。新增场景必须在这里登记，因此模型或请求参数不能越权选择任意数据源。
 */
@Component
public class ScenarioPlanRegistry {
    private final Map<ScenarioType, DiagnosisPlan> plans;

    public ScenarioPlanRegistry() {
        // EnumMap 以枚举序号索引，比通用 HashMap 更紧凑且键类型更明确。
        EnumMap<ScenarioType, DiagnosisPlan> values = new EnumMap<>(ScenarioType.class);
        register(values, ScenarioType.ORDER_INFORMATION_QUERY, "订单信息查询",
                "Order facts lookup", "ORDER", "SOP");
        register(values, ScenarioType.PRODUCT_INFORMATION_QUERY, "产品规格与兼容性查询",
                "Product knowledge lookup", "ORDER", "PRODUCT_KNOWLEDGE", "SOP");
        register(values, ScenarioType.PRODUCT_USAGE_GUIDANCE, "产品使用与保养指导",
                "Product usage guidance", "ORDER", "PRODUCT_KNOWLEDGE", "SOP");
        register(values, ScenarioType.PRODUCT_TROUBLESHOOTING, "产品故障排查与售后指引",
                "Product troubleshooting", "ORDER", "PRODUCT_KNOWLEDGE", "SOP");
        register(values, ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING, "支付成功但订单未更新",
                "Payment callback recovery", "ORDER", "PAYMENT", "SOP");
        register(values, ScenarioType.ORDER_CANCELLED_BUT_CHARGED, "订单已取消但仍扣款",
                "Cancelled order refund recovery", "ORDER", "PAYMENT", "REFUND", "SOP");
        register(values, ScenarioType.COUPON_UNAVAILABLE, "优惠券无法使用",
                "Coupon eligibility explanation", "ORDER", "COUPON", "SOP");
        register(values, ScenarioType.MEMBER_BENEFIT_NOT_RECEIVED, "会员权益未到账",
                "Member benefit grant recovery", "MEMBER", "SOP");
        register(values, ScenarioType.LOGISTICS_TRACKING_QUERY, "物流进度查询",
                "Logistics route lookup", "ORDER", "LOGISTICS", "SOP");
        register(values, ScenarioType.LOGISTICS_STATUS_NOT_SYNCED, "物流状态不同步",
                "Logistics status synchronization", "ORDER", "LOGISTICS", "SOP");
        register(values, ScenarioType.API_FREQUENT_FAILURE, "API 调用频繁失败",
                "API failure window analysis", "API_CALL", "SOP");
        register(values, ScenarioType.INVOICE_ISSUE_FAILED, "发票开具失败",
                "Invoice qualification validation", "ORDER", "INVOICE", "SOP");
        register(values, ScenarioType.PRODUCT_TRACE_ANOMALY, "产品全链路溯源异常",
                "Product trace anomaly analysis", "TRACE_IDENTITY", "TRACE_EVENT", "SOP");
        // 发布为不可变 Map，防止运行期间动态篡改场景的数据访问范围。
        plans = Map.copyOf(values);
    }

    public DiagnosisPlan required(ScenarioType scenarioType) {
        DiagnosisPlan plan = plans.get(scenarioType);
        if (plan == null) throw new BusinessException(ErrorCode.UNKNOWN_SCENARIO);
        return plan;
    }

    private void register(Map<ScenarioType, DiagnosisPlan> target, ScenarioType type,
                          String name, String title, String... querySteps) {
        // 可变参数让每个场景可以声明不同数量的查询步骤。
        target.put(type, new DiagnosisPlan(type, name, title, List.of(querySteps)));
    }
}
