package com.example.supportops.module.business.model.query;

import java.time.LocalDateTime;

public record LogisticsRecord(Long id, String trackingNo, String orderNo, String sourceType,
                              String carrierName, String logisticsStatus, String statusDescription,
                              String originLocation, String destinationLocation, String currentLocation,
                              String facilityName, String courierNameMasked, String courierPhoneMasked,
                              LocalDateTime estimatedDeliveryAt, LocalDateTime eventTime,
                              LocalDateTime syncedAt) implements BusinessQueryRecord {
    public LogisticsRecord(Long id, String trackingNo, String orderNo, String sourceType,
                           String logisticsStatus, String statusDescription, LocalDateTime eventTime,
                           LocalDateTime syncedAt) {
        this(id, trackingNo, orderNo, sourceType, null, logisticsStatus, statusDescription,
                null, null, null, null, null, null, null, eventTime, syncedAt);
    }
}
