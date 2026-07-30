package com.example.supportops.module.ai.audit;

/**
 * 将当前异步诊断与 LangChain4j Listener 关联。ThreadLocal 只保存主键和类型，不保存 Prompt。
 */
public final class AiCallScope implements AutoCloseable {
    private static final ThreadLocal<CallContext> CURRENT = new ThreadLocal<>();

    private AiCallScope(CallContext context) {
        CURRENT.set(context);
    }

    public static AiCallScope open(long logId, long diagnosisId, String callType, long startedNanos) {
        return new AiCallScope(new CallContext(logId, diagnosisId, callType, startedNanos));
    }

    public static CallContext current() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        CURRENT.remove();
    }

    public record CallContext(long logId, long diagnosisId, String callType, long startedNanos) {
    }
}
