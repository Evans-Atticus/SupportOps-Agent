package com.example.supportops.module.auth.model.bo;

public record UserBO(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String roleCode,
        String status,
        Integer dailyQuota
) {
}
