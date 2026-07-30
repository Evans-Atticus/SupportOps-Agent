package com.example.supportops.module.business.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRecord(Long id, String paymentNo, String orderNo, String channel, String paymentStatus,
                            BigDecimal amount, String callbackStatus, String callbackErrorCode,
                            Integer callbackAttempts, LocalDateTime paidAt,
                            LocalDateTime callbackAt) implements BusinessQueryRecord {
}
