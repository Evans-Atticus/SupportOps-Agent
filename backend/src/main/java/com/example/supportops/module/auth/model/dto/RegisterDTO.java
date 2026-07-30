package com.example.supportops.module.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 公开注册参数；账号只允许 URL/日志安全的字符，密码长度兼容 BCrypt 的 72 字节上限。 */
public record RegisterDTO(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "账号只能包含字母、数字、下划线和短横线，长度为 3-32 位")
        String username,
        @NotBlank @Size(min = 2, max = 50, message = "显示名称长度为 2-50 位")
        String displayName,
        @NotBlank @Size(min = 8, max = 72, message = "密码长度为 8-72 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x20-\\x7E]+$", message = "密码必须包含字母和数字，且只能使用可打印字符")
        String password
) {
}
