package com.example.supportops.module.portal.model.dto;

import jakarta.validation.constraints.Size;

public record HandoffRequestDTO(
        @Size(max = 2000) String content,
        @Size(max = 40) String ticketNo,
        @Size(max = 64) String businessNo,
        @Size(max = 64) String conversationNo,
        Long diagnosisId
) {
}
