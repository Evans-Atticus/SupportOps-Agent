package com.example.supportops.module.portal.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupportUserUpdateDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String username,
        @NotBlank @Size(min = 2, max = 50) String displayName,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @Min(1) @Max(10000) int dailyQuota
) {
}
