package com.example.supportops.module.business.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundRecord(Long id, String refundNo, String orderNo, String refundStatus,
                           BigDecimal refundAmount, String failureCode, LocalDateTime requestedAt,
                           LocalDateTime completedAt) implements BusinessQueryRecord {
}
