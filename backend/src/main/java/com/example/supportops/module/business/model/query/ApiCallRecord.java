package com.example.supportops.module.business.model.query;

import java.time.LocalDateTime;

public record ApiCallRecord(Long id, String clientCode, String apiName, String traceId, String requestStatus,
                            Integer httpStatus, String errorCode, Integer durationMs,
                            LocalDateTime calledAt) implements BusinessQueryRecord {
}
