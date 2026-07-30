package com.example.supportops.module.ticket.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.example.supportops.module.ticket.model.enums.TicketPriority;

public record TicketCreateDTO(
        @NotNull Long customerId,
        @NotBlank @Size(max = 40) String ticketNo,
        @Size(max = 64) String businessNo,
        @NotBlank @Size(max = 20) String channel,
        @NotBlank @Size(max = 2000) String description,
        TicketPriority priority
) {
}
