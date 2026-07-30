package com.example.supportops.module.diagnosis;

import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.business.model.query.SopRecord;
import com.example.supportops.module.diagnosis.handler.ProductInformationQueryHandler;
import com.example.supportops.module.diagnosis.handler.ProductTroubleshootingHandler;
import com.example.supportops.module.diagnosis.handler.ProductUsageGuidanceHandler;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeSnippet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductKnowledgeHandlersTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 14, 0);

    @Test
    void answersFromRetrievedSkuDocumentAndKeepsSourceEvidence() {
        ProductKnowledgeSnippet snippet = new ProductKnowledgeSnippet(11, 21, "SKU-A018", "SPECIFICATION",
                "智能耳机Pro规格说明.md", 0, "防护等级为 IPX5，可抵御运动时汗水和小雨，不可浸水。", 8);
        var result = new ProductInformationQueryHandler().diagnose(context(
                ScenarioType.PRODUCT_INFORMATION_QUERY, List.of(snippet)));
        assertTrue(result.customerReply().contains("IPX5"));
        assertTrue(result.customerReply().contains("智能耳机Pro规格说明.md"));
        assertEquals("product_knowledge_chunks", result.evidences().get(0).source());
    }

    @Test
    void refusesToInventWhenSkuHasNoIndexedDocument() {
        var result = new ProductTroubleshootingHandler().diagnose(context(
                ScenarioType.PRODUCT_TROUBLESHOOTING, List.of()));
        assertTrue(result.customerReply().contains("暂未覆盖"));
        assertTrue(result.internalSuggestion().contains("不得使用模型常识"));
    }

    @Test
    void newPhoneQuestionOnlyReturnsPairingSection() {
        ProductKnowledgeSnippet snippet = new ProductKnowledgeSnippet(11, 21, "SKU-A018", "PRODUCT_MANUAL",
                "智能耳机Pro说明.md", 0, """
                ## 规格与兼容性
                防护等级 IPX5，综合续航约 28 小时。
                ## 首次连接
                关闭旧手机蓝牙或忽略该耳机。打开充电盒，长按配对键 3 秒，再在新手机选择 SupportOps Buds Pro。
                ## 无法充电与异常发热排查
                立即停止使用并断开充电线。
                """, 9);

        var result = new ProductUsageGuidanceHandler().diagnose(context(
                ScenarioType.PRODUCT_USAGE_GUIDANCE, "这款耳机怎么连接新手机？", List.of(snippet)));

        assertTrue(result.customerReply().contains("关闭旧手机蓝牙"), result.customerReply());
        assertTrue(result.customerReply().contains("长按配对键 3 秒"), result.customerReply());
        assertTrue(!result.customerReply().contains("综合续航"), result.customerReply());
        assertTrue(!result.customerReply().contains("异常发热"), result.customerReply());
    }

    @Test
    void safetyQuestionKeepsStopUseAndAfterSalesInstructions() {
        ProductKnowledgeSnippet snippet = new ProductKnowledgeSnippet(12, 22, "SKU-A018", "AFTER_SALES_SOP",
                "智能耳机Pro售后指引.md", 0, """
                ## 首次连接
                长按配对键 3 秒进入配对模式。
                ## 无法充电与异常发热排查
                1. 立即停止使用并断开充电线，等待自然冷却；不要拆机、挤压或浸水降温。
                2. 若仍明显发热、出现异味、鼓包或外壳变形，请勿再次充电，保留订单信息并联系人工客服安排安全检测。
                """, 12);

        var result = new ProductTroubleshootingHandler().diagnose(context(
                ScenarioType.PRODUCT_TROUBLESHOOTING,
                "耳机盒无法充电，而且已经异常发热并有异味，我应该怎么办？", List.of(snippet)));

        assertTrue(result.customerReply().contains("立即停止使用并断开充电线"), result.customerReply());
        assertTrue(result.customerReply().contains("请勿再次充电"), result.customerReply());
        assertTrue(result.customerReply().contains("联系人工客服安排安全检测"), result.customerReply());
        assertTrue(!result.customerReply().contains("配对模式"), result.customerReply());
        assertTrue(!result.customerReply().contains("未能完整显示"), result.customerReply());
    }

    @Test
    void anotherSkuUsesItsOwnSafetySopWithoutLeakingEarphoneInstructions() {
        ProductKnowledgeSnippet snippet = new ProductKnowledgeSnippet(30, 40, "SKU-C203", "AFTER_SALES_SOP",
                "便携充电宝售后SOP.md", 0, """
                ## 无法充电排查
                更换确认可用的数据线和充电器，检查接口是否有异物。
                ## 售后处理 SOP
                鼓包、冒烟、进水等安全风险不得引导客户继续测试，应立即停止使用并转人工处理。
                未经检测不得承诺必然换新或退款。
                """, 15);

        var result = new ProductTroubleshootingHandler().diagnose(context(
                ScenarioType.PRODUCT_TROUBLESHOOTING, "SKU-C203 便携充电宝",
                "充电宝鼓包并且有异味，应该怎么办？", List.of(snippet)));

        assertTrue(result.customerReply().contains("鼓包、冒烟、进水"), result.customerReply());
        assertTrue(result.customerReply().contains("转人工处理"), result.customerReply());
        assertTrue(!result.customerReply().contains("耳机"), result.customerReply());
        assertTrue(!result.customerReply().contains("配对"), result.customerReply());
    }

    @Test
    void missingProductSafetySopUsesOnlyGenericBoundaryAndEscalates() {
        ProductKnowledgeSnippet snippet = new ProductKnowledgeSnippet(31, 41, "SKU-X001", "SPECIFICATION",
                "未知产品规格.md", 0, "## 产品规格\n额定容量 5000mAh。", 3);

        var result = new ProductTroubleshootingHandler().diagnose(context(
                ScenarioType.PRODUCT_TROUBLESHOOTING, "SKU-X001 未知产品",
                "这个产品冒烟了怎么办？", List.of(snippet)));

        assertTrue(result.customerReply().contains("立即停止使用"), result.customerReply());
        assertTrue(result.customerReply().contains("不会推测该产品的专属处理步骤"), result.customerReply());
        assertTrue(result.internalSuggestion().contains("转人工"), result.internalSuggestion());
        assertTrue(!result.customerReply().contains("5000mAh"), result.customerReply());
    }

    @Test
    void electricalSafetyTermsUseTheCurrentProductsOwnSop() {
        ProductKnowledgeSnippet snippet = new ProductKnowledgeSnippet(32, 42, "SKU-Z902", "AFTER_SALES_SOP",
                "台式设备安全SOP.md", 0, """
                ## 漏电与火花处置
                出现漏电、触电感或火花时，禁止继续通电测试；在无需接触异常部位的情况下切断电源，
                让人员远离设备并联系人工售后安排送检。
                """, 13);

        var result = new ProductTroubleshootingHandler().diagnose(context(
                ScenarioType.PRODUCT_TROUBLESHOOTING, "SKU-Z902 台式设备",
                "设备外壳漏电而且出现火花，应该怎么办？", List.of(snippet)));

        assertTrue(result.customerReply().contains("禁止继续通电测试"), result.customerReply());
        assertTrue(result.customerReply().contains("切断电源"), result.customerReply());
        assertTrue(result.customerReply().contains("联系人工售后安排送检"), result.customerReply());
        assertTrue(!result.customerReply().contains("耳机"), result.customerReply());
        assertTrue(!result.customerReply().contains("充电宝"), result.customerReply());
    }

    private DiagnosisContext context(ScenarioType scenario, List<ProductKnowledgeSnippet> snippets) {
        return context(scenario, "", snippets);
    }

    private DiagnosisContext context(ScenarioType scenario, String question, List<ProductKnowledgeSnippet> snippets) {
        return context(scenario, "SKU-A018 智能耳机 Pro", question, snippets);
    }

    private DiagnosisContext context(ScenarioType scenario, String productScope, String question,
                                     List<ProductKnowledgeSnippet> snippets) {
        OrderRecord order = new OrderRecord(1L, "O202607060001", "C01", "COMPLETED", "PAID",
                new BigDecimal("299"), new BigDecimal("299"), "CNY", null,
                productScope, NOW, null, NOW);
        SopRecord sop = new SopRecord(1L, scenario.name(), "产品知识", "CUSTOMER", 1, "[]", true, NOW);
        return new DiagnosisContext(scenario, "TK-1", order.orderNo(), NOW, List.of(order), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), snippets, sop, null, question);
    }
}
