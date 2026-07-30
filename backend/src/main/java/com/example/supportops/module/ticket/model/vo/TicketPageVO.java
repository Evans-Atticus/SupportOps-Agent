package com.example.supportops.module.ticket.model.vo;

import java.util.List;

public record TicketPageVO(long page, long size, long total, List<TicketVO> records) {
}
