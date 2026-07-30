package com.example.supportops.module.portal.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundDecisionDTO(
        @DecimalMin("0.00") BigDecimal approvedAmount,
        @Size(max = 500) String reason
) {
}
