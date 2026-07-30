package com.example.supportops.module.ai.fallback;

import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeywordFallbackClassifierTests {
    private final KeywordFallbackClassifier classifier = new KeywordFallbackClassifier();

    @Test
    void currentQuestionOverridesHistoricalTicketHint() {
        assertEquals(ScenarioType.LOGISTICS_TRACKING_QUERY,
                classifier.classify("帮我查询一下物流可以吗？", "PAYMENT_SUCCESS_ORDER_PENDING"));
    }

    @Test
    void logisticsTrackingAndSynchronizationAreDifferentIntents() {
        assertEquals(ScenarioType.LOGISTICS_TRACKING_QUERY,
                classifier.classify("我的包裹到哪里了，预计什么时候送达？", "LOGISTICS_STATUS_NOT_SYNCED"));
        assertEquals(ScenarioType.LOGISTICS_STATUS_NOT_SYNCED,
                classifier.classify("快递已经派送，平台物流为什么还未同步？", "ORDER_INFORMATION_QUERY"));
    }

    @Test
    void ticketHintRemainsFallbackForAmbiguousQuestion() {
        assertEquals(ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING,
                classifier.classify("帮我看一下这个问题", "PAYMENT_SUCCESS_ORDER_PENDING"));
    }

    @Test
    void orderAmountQueryOverridesHistoricalPaymentHint() {
        assertEquals(ScenarioType.ORDER_INFORMATION_QUERY,
                classifier.classify("查询金额", "PAYMENT_SUCCESS_ORDER_PENDING"));
        assertEquals(ScenarioType.ORDER_INFORMATION_QUERY,
                classifier.classify("请查询这个订单的金额和支付状态", "LOGISTICS_STATUS_NOT_SYNCED"));
    }

    @Test
    void explicitPaymentAnomalyIsNotReducedToAStatusLookup() {
        assertEquals(ScenarioType.PAYMENT_SUCCESS_ORDER_PENDING,
                classifier.classify("已经支付成功，但订单状态仍显示待支付", "ORDER_INFORMATION_QUERY"));
    }

    @Test
    void preservesEveryQuestionInACompoundRequest() {
        assertEquals(List.of(ScenarioType.ORDER_INFORMATION_QUERY, ScenarioType.LOGISTICS_TRACKING_QUERY),
                classifier.classifyAll("查询这个订单的价格和物流", "PAYMENT_SUCCESS_ORDER_PENDING"));
    }

    @Test
    void separatesProductFactsUsageAndTroubleshooting() {
        assertEquals(ScenarioType.PRODUCT_INFORMATION_QUERY,
                classifier.classify("这款耳机防水吗，续航多久", null));
        assertEquals(ScenarioType.PRODUCT_USAGE_GUIDANCE,
                classifier.classify("这个耳机怎么连接新手机", null));
        assertEquals(ScenarioType.PRODUCT_TROUBLESHOOTING,
                classifier.classify("耳机充不进电而且一直发热", null));
    }

    @Test
    void recognizesSafetyRisksAcrossUnrelatedProductCategories() {
        assertEquals(ScenarioType.PRODUCT_TROUBLESHOOTING,
                classifier.classify("电饭煲外壳漏电并出现火花", null));
        assertEquals(ScenarioType.PRODUCT_TROUBLESHOOTING,
                classifier.classify("儿童玩具里的电池漏液了怎么办", null));
        assertEquals(ScenarioType.PRODUCT_TROUBLESHOOTING,
                classifier.classify("显示器突然冒烟并有焦味", null));
    }

    @Test
    void productQuestionsCanCoexistWithOrderAndLogisticsQuestions() {
        assertEquals(List.of(ScenarioType.ORDER_INFORMATION_QUERY, ScenarioType.PRODUCT_INFORMATION_QUERY,
                        ScenarioType.LOGISTICS_TRACKING_QUERY),
                classifier.classifyAll("多少钱，是否防水，物流到哪里了", null));
    }
}
