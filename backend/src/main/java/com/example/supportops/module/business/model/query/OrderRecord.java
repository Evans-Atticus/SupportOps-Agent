package com.example.supportops.module.business.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderRecord(Long id, String orderNo, String customerNo, String orderStatus, String paymentStatus,
                          BigDecimal totalAmount, BigDecimal payableAmount, String currency, String couponCode,
                          String productScope, LocalDateTime paidAt, LocalDateTime cancelledAt,
                          LocalDateTime updatedAt) implements BusinessQueryRecord {
}
