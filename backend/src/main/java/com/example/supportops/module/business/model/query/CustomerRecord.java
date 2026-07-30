package com.example.supportops.module.business.model.query;

import java.time.LocalDateTime;

public record CustomerRecord(Long id, String customerNo, String customerName, String mobileMasked,
                             String emailMasked, String status, LocalDateTime createdAt,
                             LocalDateTime updatedAt) implements BusinessQueryRecord {
}
