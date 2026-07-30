package com.example.supportops.module.ai.mock;

import com.example.supportops.module.ai.reply.CustomerReplyAiService;
import com.example.supportops.module.ai.reply.ReplyDraft;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Mock 模式不生成新事实，只把已验证上下文包装为固定语气。 */
@Service
@ConditionalOnProperty(name = "supportops.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockCustomerReplyAiService implements CustomerReplyAiService {
    @Override
    public ReplyDraft generate(String verifiedDiagnosisContext) {
        String verifiedDraft = sectionValue(verifiedDiagnosisContext, "后端事实答复草稿：", "已验证证据：");
        if (verifiedDraft != null) return new ReplyDraft(verifiedDraft, "professional");
        String conclusion = optionalLineValue(verifiedDiagnosisContext, "规则结论：");
        if (conclusion == null) conclusion = lineValue(verifiedDiagnosisContext, "结论：");
        String nextStep = lineValue(verifiedDiagnosisContext, "对客可见下一步：");
        String content = "您好，理解您遇到的问题。经核查，" + conclusion
                + "。" + nextStep + "，请勿重复提交相同操作。";
        return new ReplyDraft(content, "professional");
    }

    private String lineValue(String context, String prefix) {
        String value = optionalLineValue(context, prefix);
        return value == null ? "相关信息正在核查" : value;
    }

    private String optionalLineValue(String context, String prefix) {
        if (context == null) return null;
        return context.lines().filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim()).findFirst().orElse(null);
    }

    private String sectionValue(String context, String start, String end) {
        if (context == null) return null;
        int startAt = context.indexOf(start);
        if (startAt < 0) return null;
        startAt += start.length();
        int endAt = context.indexOf(end, startAt);
        String value = (endAt < 0 ? context.substring(startAt) : context.substring(startAt, endAt)).trim();
        return value.isBlank() ? null : value;
    }
}
