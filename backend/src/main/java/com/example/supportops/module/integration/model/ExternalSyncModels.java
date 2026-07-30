package com.example.supportops.module.integration.model;

import java.time.OffsetDateTime;

public final class ExternalSyncModels {
    private ExternalSyncModels() {
    }

    public record SyncReservationVO(
            String integration,
            String resourceType,
            String status,
            boolean configured,
            String message,
            String requestedBy,
            OffsetDateTime requestedAt,
            String nextStep
    ) {
    }
}
