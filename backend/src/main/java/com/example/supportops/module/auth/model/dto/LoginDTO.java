package com.example.supportops.module.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 100) String password
) {
}
