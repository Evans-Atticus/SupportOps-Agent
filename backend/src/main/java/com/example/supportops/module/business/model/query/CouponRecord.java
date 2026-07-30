package com.example.supportops.module.business.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRecord(Long id, String couponCode, String couponName, String couponStatus,
                           BigDecimal thresholdAmount, BigDecimal discountAmount, String productScope,
                           LocalDateTime validFrom, LocalDateTime validUntil, String receiveStatus,
                           LocalDateTime receivedAt, String customerNo) implements BusinessQueryRecord {
}
