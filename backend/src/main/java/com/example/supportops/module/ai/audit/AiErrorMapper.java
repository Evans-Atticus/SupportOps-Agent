package com.example.supportops.module.ai.audit;

import com.example.supportops.common.exception.ErrorCode;

import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** 将不同供应商异常归一化为稳定的项目错误码。 */
public final class AiErrorMapper {
    private AiErrorMapper() {
    }

    public static ErrorCode code(Throwable error) {
        Throwable current = error;
        StringBuilder details = new StringBuilder();
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return ErrorCode.AI_TIMEOUT;
            }
            details.append(' ').append(current.getClass().getName())
                    .append(' ').append(String.valueOf(current.getMessage()));
            current = current.getCause();
        }
        String text = details.toString().toLowerCase(Locale.ROOT);
        // 百炼可能同时返回 HTTP 429，因此额度错误必须先于普通限流判断。
        if (text.contains("insufficient_quota") || text.contains("quota exhausted")
                || text.contains("quota exceeded") || text.contains("insufficient balance")
                || text.contains("arrearage") || text.contains("余额不足") || text.contains("额度已用完")) {
            return ErrorCode.AI_QUOTA_EXHAUSTED;
        }
        if (text.contains("429") || text.contains("rate limit")) return ErrorCode.AI_RATE_LIMITED;
        if (text.contains("quota") || text.contains("insufficient")) return ErrorCode.AI_QUOTA_EXHAUSTED;
        if (text.contains("parse") || text.contains("json") || text.contains("validation")) {
            return ErrorCode.AI_RESPONSE_PARSE_FAILED;
        }
        return ErrorCode.AI_UNAVAILABLE;
    }
}
