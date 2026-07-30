package com.example.supportops.module.ai.mock;

import com.example.supportops.module.ai.understanding.TicketIntent;
import com.example.supportops.module.ai.understanding.TicketUnderstandingAiService;
import com.example.supportops.module.ai.fallback.KeywordFallbackClassifier;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 无 API Key 可运行的确定性理解服务，用于本地开发和 CI。 */
@Service
@ConditionalOnProperty(name = "supportops.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockTicketUnderstandingAiService implements TicketUnderstandingAiService {
    private static final Pattern BUSINESS_NO = Pattern.compile(
            "(?:O|M|SF|CLIENT-|LOT-|TR1-|QC-|IN-|OUT-|PO-|SKU-)[A-Z0-9-]{3,}", Pattern.CASE_INSENSITIVE);
    private final KeywordFallbackClassifier classifier = new KeywordFallbackClassifier();

    @Override
    public TicketIntent understand(String description) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        List<ScenarioType> scenarios = classifier.classifyAll(description, null);
        ScenarioType scenario = scenarios.stream().findFirst().orElse(ScenarioType.UNKNOWN);
        double confidence = scenario == ScenarioType.UNKNOWN ? 0.25 : 0.96;
        List<String> missing = scenario == ScenarioType.UNKNOWN ? List.of("请补充具体业务现象和业务编号") : List.of();
        return new TicketIntent(scenario, scenarios, extractBusinessNo(description), abbreviate(description),
                detectEmotion(text), confidence, missing);
    }

    private String extractBusinessNo(String description) {
        if (description == null) return null;
        Matcher matcher = BUSINESS_NO.matcher(description);
        return matcher.find() ? matcher.group() : null;
    }

    private String abbreviate(String description) {
        if (description == null || description.isBlank()) return "未提供问题描述";
        return description.length() <= 120 ? description : description.substring(0, 120);
    }

    private String detectEmotion(String text) {
        if (text.contains("投诉") || text.contains("愤怒")) return "angry";
        if (text.contains("尽快") || text.contains("紧急") || text.contains("频繁")) return "urgent";
        if (text.contains("一直") || text.contains("没有收到")) return "anxious";
        return "neutral";
    }
}
