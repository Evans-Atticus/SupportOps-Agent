package com.example.supportops.module.ticket.service;

import com.example.supportops.module.ticket.model.dto.TicketCreateDTO;
import com.example.supportops.module.ticket.model.dto.TicketUpdateDTO;
import com.example.supportops.module.ticket.model.enums.TicketStatus;
import com.example.supportops.module.ticket.model.vo.TicketPageVO;
import com.example.supportops.module.ticket.model.vo.TicketVO;

public interface TicketService {
    TicketVO create(TicketCreateDTO createDTO);

    TicketVO get(Long id);

    TicketPageVO list(long page, long size, TicketStatus status);

    TicketVO update(Long id, TicketUpdateDTO updateDTO);

    void delete(Long id);
}
