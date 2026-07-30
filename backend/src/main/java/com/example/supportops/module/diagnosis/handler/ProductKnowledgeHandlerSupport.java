package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.business.model.query.OrderRecord;
import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisEvidence;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeSnippet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

abstract class ProductKnowledgeHandlerSupport implements ScenarioDiagnosisHandler {
    private static final List<String> SAFETY_RISK_TERMS = List.of(
            "发热", "过热", "烫手", "异味", "焦味", "鼓包", "膨胀", "冒烟", "起火", "爆炸", "爆裂",
            "漏液", "漏电", "触电", "进水", "短路", "火花");

    protected DiagnosisResult diagnoseKnowledge(DiagnosisContext context, String subject) {
        OrderRecord order = context.orders().get(0);
        boolean safetyQuestion = isSafetyQuestion(context.customerQuestion());
        if (context.productKnowledge().isEmpty()) {
            if (safetyQuestion) return safetyFallback(order, "当前 SKU 没有命中可用的安全处置资料");
            String reply = "已定位到订单 " + order.orderNo() + " 中的商品“" + order.productScope()
                    + "”，但该商品已索引的产品资料暂未覆盖您询问的" + subject
                    + "。为避免给出不准确答案，请补充更具体的型号或现象，也可以转人工进一步核实。";
            return new DiagnosisResult("产品知识库未命中", reply,
                    "不得使用模型常识补写产品事实；应补充该 SKU 的权威资料或转人工核实。",
                    reply, 0.65, List.of(HandlerSupport.evidence("biz_orders", order.id(), "product_scope",
                    "已定位商品", order.productScope(), "订单号：" + order.orderNo())));
        }

        if (safetyQuestion) return diagnoseSafety(context, order);

        List<DiagnosisEvidence> evidences = new ArrayList<>();
        StringBuilder reply = new StringBuilder("根据商品“").append(order.productScope())
                .append("”的已索引资料，为您找到以下与").append(subject).append("相关的内容：");
        int number = 1;
        for (ProductKnowledgeSnippet snippet : context.productKnowledge().stream().limit(2).toList()) {
            String focused = focusedContent(snippet.content(), context.customerQuestion());
            String content = abbreviate(cleanForReply(focused), 420);
            reply.append("\n").append(number++).append(". ").append(content)
                    .append("（来源：").append(snippet.documentName()).append("）");
            evidences.add(new DiagnosisEvidence("product_knowledge_chunks", snippet.documentId(), "chunk_text",
                    "产品资料 · " + snippet.documentName(), abbreviate(focused, 600),
                    "SKU=" + snippet.productSku() + "，文档类型=" + snippet.documentType()
                            + "，片段=" + (snippet.chunkIndex() + 1), 0.95));
        }
        String conclusion = "已按 SKU " + context.productKnowledge().get(0).productSku()
                + " 检索产品知识附件，命中 " + Math.min(2, context.productKnowledge().size()) + " 个相关片段";
        return new DiagnosisResult("已按 SKU 检索产品知识附件", conclusion,
                "回答必须覆盖客户原问题，并仅使用已召回的产品资料片段；不得用常识补齐未知参数。",
                reply.toString(), 0.95, evidences);
    }

    private String abbreviate(String value, int limit) {
        String safe = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return safe.length() <= limit ? safe : safe.substring(0, limit) + "…";
    }

    private String cleanForReply(String value) {
        return value == null ? "" : value.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^[-*]\\s+", "• ").trim();
    }

    /** 从召回片段中再次按客户原问题选择小节，避免整份说明书进入回复上下文。 */
    private String focusedContent(String content, String question) {
        if (content == null || content.isBlank() || question == null || question.isBlank()) return content;
        String[] sections = content.split("(?m)(?=^#{1,6}\\s+)");
        if (sections.length <= 1) return content;
        Set<String> terms = terms(question);
        return java.util.Arrays.stream(sections)
                .map(section -> new ScoredSection(section, score(section, terms)))
                .filter(section -> section.score > 0)
                .sorted(Comparator.comparingInt(ScoredSection::score).reversed())
                .limit(isSafetyQuestion(question) ? 2 : 1)
                .map(ScoredSection::content)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(content);
    }

    private Set<String> terms(String question) {
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String keyword : List.of("连接", "配对", "新手机", "使用", "安装", "保养", "充电", "无法充电",
                "发热", "异常发热", "过热", "烫手", "异味", "焦味", "鼓包", "膨胀", "冒烟", "起火",
                "爆炸", "爆裂", "漏液", "漏电", "触电", "进水", "短路", "火花", "售后", "防水", "续航")) {
            if (normalized.contains(keyword)) values.add(keyword);
        }
        for (int index = 0; index + 1 < normalized.length(); index++) {
            String term = normalized.substring(index, index + 2);
            if (term.chars().allMatch(character -> Character.UnicodeScript.of(character)
                    == Character.UnicodeScript.HAN)) values.add(term);
        }
        return values;
    }

    private int score(String section, Set<String> terms) {
        String normalized = section.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) if (normalized.contains(term)) score += term.length() >= 3 ? 3 : 1;
        return score;
    }

    private boolean isSafetyQuestion(String question) {
        return question != null && SAFETY_RISK_TERMS.stream().anyMatch(question::contains);
    }

    /**
     * 安全问题先套用跨产品的最小风险边界，再追加当前 SKU 文档中明确写出的步骤。
     * 没有产品级安全资料时不根据其他 SKU 或模型常识补写。
     */
    private DiagnosisResult diagnoseSafety(DiagnosisContext context, OrderRecord order) {
        List<DiagnosisEvidence> evidences = new ArrayList<>();
        List<String> verifiedSections = new ArrayList<>();
        for (ProductKnowledgeSnippet snippet : context.productKnowledge().stream().limit(2).toList()) {
            String focused = focusedContent(snippet.content(), context.customerQuestion());
            if (!containsSafetyGuidance(focused)) continue;
            verifiedSections.add(cleanForReply(focused));
            evidences.add(new DiagnosisEvidence("product_knowledge_chunks", snippet.documentId(), "chunk_text",
                    "安全与售后资料 · " + snippet.documentName(), abbreviate(focused, 700),
                    "SKU=" + snippet.productSku() + "，文档类型=" + snippet.documentType()
                            + "，片段=" + (snippet.chunkIndex() + 1), 0.98));
        }
        if (verifiedSections.isEmpty()) {
            return safetyFallback(order, "已召回资料未包含可验证的安全处置步骤");
        }

        StringBuilder reply = new StringBuilder("检测到您描述了可能涉及安全风险的现象。请立即停止使用；")
                .append("在确保人身安全且无需接触异常部位的前提下，断开电源或停止充电。")
                .append("不要继续测试、充电、挤压、拆机或用水处理。\n\n")
                .append("根据商品“").append(order.productScope()).append("”已索引的安全与售后资料：\n")
                .append(String.join("\n", verifiedSections));
        return new DiagnosisResult("产品安全风险处置", "已命中当前 SKU 的安全与售后资料",
                "停止远程试错并转人工客服；后续动作仅以当前 SKU 的权威售后资料为依据。",
                reply.toString(), 0.99, evidences);
    }

    private DiagnosisResult safetyFallback(OrderRecord order, String reason) {
        String reply = "检测到您描述了可能涉及安全风险的现象。请立即停止使用；"
                + "在确保人身安全且无需接触异常部位的前提下，断开电源或停止充电。"
                + "不要继续测试、充电、挤压、拆机或用水处理，也不要为了拍摄或搬动而接触异常部位。"
                + "请让人员远离异常产品，并立即联系人工客服或品牌售后。"
                + "如果已经冒烟、起火或无法安全处置，请立即远离并联系当地消防或紧急服务。"
                + reason + "，为避免误导，智能体不会推测该产品的专属处理步骤。";
        return new DiagnosisResult("产品安全风险升级人工", reason,
                "立即停止远程排查并转人工；补充该 SKU 的权威安全与售后资料后再提供产品级步骤。",
                reply, 0.90, List.of(HandlerSupport.evidence("biz_orders", order.id(), "product_scope",
                "已定位商品", order.productScope(), "订单号：" + order.orderNo())));
    }

    private boolean containsSafetyGuidance(String content) {
        if (content == null || content.isBlank()) return false;
        boolean hasRisk = SAFETY_RISK_TERMS.stream().anyMatch(content::contains);
        boolean hasAction = List.of("停止", "断开", "断电", "切断", "拔掉", "不得", "不要", "禁止", "远离",
                        "隔离", "联系", "检测", "送检", "送修")
                .stream().anyMatch(content::contains);
        return hasRisk && hasAction;
    }

    private record ScoredSection(String content, int score) {
    }
}
