package com.example.supportops.module.ai.audit;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** LangChain4j 真实模型监听器：记录耗时、模型名、Token 和错误类别。 */
@Component
@ConditionalOnProperty(name = "supportops.ai.mode", havingValue = "real")
public class SupportOpsChatModelListener implements ChatModelListener {
    private final ModelCallLogRepository repository;

    public SupportOpsChatModelListener(ModelCallLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        // 出于隐私与安全要求，不记录 context.chatRequest().messages()。
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        AiCallScope.CallContext call = AiCallScope.current();
        if (call == null) return;
        TokenUsage usage = context.chatResponse().tokenUsage();
        repository.success(call.logId(), context.chatResponse().modelName(),
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount(), elapsed(call));
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        AiCallScope.CallContext call = AiCallScope.current();
        if (call == null) return;
        repository.failure(call.logId(), AiErrorMapper.code(context.error()).name(), elapsed(call));
    }

    private long elapsed(AiCallScope.CallContext call) {
        return Math.max(0, (System.nanoTime() - call.startedNanos()) / 1_000_000);
    }
}
