package com.example.supportops.module.portal.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PortalQueryRecords {
    private PortalQueryRecords() {
    }

    public record UserContextRecord(long userId, String roleCode, Long customerId) {
    }

    public record DashboardCountsRecord(long first, long second, long third, long fourth) {
    }

    public record ItemRecord(
            String id,
            String title,
            String detail,
            String meta,
            String status,
            LocalDateTime occurredAt
    ) {
    }

    public record LogisticsTimelineRecord(
            long id, String trackingNo, String orderNo, String customerName, String product,
            String sourceType, String carrierName, String logisticsStatus, String statusDescription,
            String originLocation, String destinationLocation, String currentLocation, String facilityName,
            String courierNameMasked, String courierPhoneMasked, LocalDateTime estimatedDeliveryAt,
            LocalDateTime eventTime, LocalDateTime syncedAt
    ) {
    }

    public record RefundRecord(
            long id,
            String refundNo,
            String customerName,
            String orderNo,
            String product,
            BigDecimal orderAmount,
            BigDecimal requestedAmount,
            BigDecimal approvedAmount,
            String status,
            String riskLevel,
            String riskMessage,
            String refundChannel,
            String rejectReason,
            LocalDateTime requestedAt,
            LocalDateTime reviewedAt,
            LocalDateTime expectedArrivalAt,
            LocalDateTime completedAt
    ) {
    }

    public record ConversationContextRecord(
            long conversationId,
            String conversationNo,
            String customerName,
            String ticketNo,
            String orderNo,
            BigDecimal orderAmount,
            BigDecimal refundableAmount,
            String refundChannel,
            String serviceMode,
            Long diagnosisId,
            String scenarioHint,
            String refundStatus,
            String diagnosisReply
    ) {
        public ConversationContextRecord(long conversationId, String conversationNo, String customerName,
                                         String ticketNo, String orderNo, BigDecimal orderAmount,
                                         BigDecimal refundableAmount, String refundChannel, String serviceMode,
                                         Long diagnosisId, String scenarioHint, String refundStatus) {
            this(conversationId, conversationNo, customerName, ticketNo, orderNo, orderAmount,
                    refundableAmount, refundChannel, serviceMode, diagnosisId, scenarioHint, refundStatus, null);
        }
    }

    public record ConversationMessageRecord(
            long id,
            String senderType,
            String content,
            LocalDateTime sentAt
    ) {
    }

    public record ConversationAttachmentRecord(
            long id,
            long messageId,
            String fileName,
            String contentType,
            long sizeBytes
    ) {
    }

    public record CustomerConversationRecord(
            long conversationId,
            String conversationNo,
            String serviceMode,
            String assignedAgent
    ) {
    }

    public record AvailableAgentRecord(long userId, String displayName) {
    }
}
