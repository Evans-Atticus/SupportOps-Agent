package com.example.supportops.module.business.model.query;

import java.time.LocalDateTime;

public record SopRecord(Long id, String scenarioType, String title, String audience, Integer version,
                        String content, Boolean enabled, LocalDateTime updatedAt) implements BusinessQueryRecord {
}
