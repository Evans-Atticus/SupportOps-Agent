package com.example.supportops.common.response;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("OK", "success", data, requestId, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, T data, String requestId) {
        return new ApiResponse<>(code, message, data, requestId, OffsetDateTime.now());
    }
}
