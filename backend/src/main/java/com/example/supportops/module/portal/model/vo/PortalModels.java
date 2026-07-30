package com.example.supportops.module.portal.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 三角色门户使用的稳定 API 输出模型。 */
public final class PortalModels {
    private PortalModels() {
    }

    public record MetricVO(String code, String label, String value, String note, String tone) {
    }

    public record DashboardVO(String role, List<MetricVO> metrics) {
    }

    public record ItemVO(
            String id,
            String title,
            String detail,
            String meta,
            String status,
            LocalDateTime occurredAt,
            Map<String, Object> extra
    ) {
        public ItemVO(String id, String title, String detail, String meta, String status,
                      LocalDateTime occurredAt) {
            this(id, title, detail, meta, status, occurredAt, Map.of());
        }
    }

    public record RefundVO(
            String refundNo,
            String customerName,
            String orderNo,
            String product,
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

    public record SearchResultVO(String module, String keyword, int total, List<ItemVO> items) {
    }

    public record ExportVO(String fileName, String contentType, String content) {
    }

    public record AdviceVO(String title, String content, String suggestedAction) {
    }

    public record OffboardResultVO(long userId, String status, int reassignedConversations) {
    }

    public record ConversationAttachmentVO(
            long id,
            String fileName,
            String contentType,
            long sizeBytes
    ) {
    }

    public record ConversationMessageVO(
            long id,
            String senderType,
            String content,
            LocalDateTime sentAt,
            List<ConversationAttachmentVO> attachments
    ) {
    }

    public record ConversationContextVO(
            String conversationNo,
            String customerName,
            String ticketNo,
            String orderNo,
            BigDecimal orderAmount,
            BigDecimal refundableAmount,
            String refundChannel,
            String serviceMode,
            String suggestedReply,
            List<ConversationMessageVO> messages
    ) {
    }

    public record CustomerConversationVO(
            String conversationNo,
            String serviceMode,
            String assignedAgent,
            List<ConversationMessageVO> messages
    ) {
    }

    public record HandoffResultVO(
            String conversationNo,
            String status,
            String assignedAgent
    ) {
    }
}
