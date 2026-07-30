package com.example.supportops.module.ticket.model.dto;

import jakarta.validation.constraints.Size;
import com.example.supportops.module.ticket.model.enums.TicketPriority;
import com.example.supportops.module.ticket.model.enums.TicketStatus;

public record TicketUpdateDTO(
        @Size(max = 64) String businessNo,
        @Size(max = 20) String channel,
        @Size(max = 2000) String description,
        TicketStatus status,
        TicketPriority priority
) {
}
