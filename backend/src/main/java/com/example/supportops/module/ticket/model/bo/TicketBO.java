package com.example.supportops.module.ticket.model.bo;

import com.example.supportops.module.ticket.model.enums.TicketPriority;
import com.example.supportops.module.ticket.model.enums.TicketStatus;

import java.time.LocalDateTime;

public record TicketBO(
        Long id,
        String ticketNo,
        Long customerId,
        String businessNo,
        String channel,
        String description,
        String scenarioHint,
        TicketStatus status,
        TicketPriority priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
