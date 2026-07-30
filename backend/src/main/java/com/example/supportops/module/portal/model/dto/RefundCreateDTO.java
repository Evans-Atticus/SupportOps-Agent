package com.example.supportops.module.portal.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundCreateDTO(
        @NotBlank @Size(max = 64) String orderNo,
        @Size(max = 40) String ticketNo,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 32) String refundChannel
) {
}
