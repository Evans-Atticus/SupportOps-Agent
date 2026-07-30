package com.example.supportops.module.portal.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupportUserCreateDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String username,
        @NotBlank @Size(min = 2, max = 50) String displayName,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x20-\\x7E]{8,72}$")
        String password
) {
}
