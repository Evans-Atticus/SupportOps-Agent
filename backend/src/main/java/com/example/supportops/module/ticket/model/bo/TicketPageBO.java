package com.example.supportops.module.ticket.model.bo;

import java.util.List;

public record TicketPageBO(long page, long size, long total, List<TicketBO> records) {
}
