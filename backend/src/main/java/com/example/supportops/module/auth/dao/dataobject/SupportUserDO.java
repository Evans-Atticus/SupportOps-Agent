package com.example.supportops.module.auth.dao.dataobject;

public record SupportUserDO(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String roleCode,
        String status,
        Integer dailyQuota
) {
}
