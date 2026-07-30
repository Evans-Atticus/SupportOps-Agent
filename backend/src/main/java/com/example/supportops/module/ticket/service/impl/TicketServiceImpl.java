package com.example.supportops.module.ticket.service.impl;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.ticket.convert.TicketConvert;
import com.example.supportops.module.ticket.manager.TicketManager;
import com.example.supportops.module.ticket.model.bo.TicketBO;
import com.example.supportops.module.ticket.model.dto.TicketCreateDTO;
import com.example.supportops.module.ticket.model.dto.TicketUpdateDTO;
import com.example.supportops.module.ticket.model.enums.TicketStatus;
import com.example.supportops.module.ticket.model.vo.TicketPageVO;
import com.example.supportops.module.ticket.model.vo.TicketVO;
import com.example.supportops.module.ticket.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TicketServiceImpl implements TicketService {
    private final TicketManager ticketManager;

    public TicketServiceImpl(TicketManager ticketManager) {
        this.ticketManager = ticketManager;
    }

    @Override
    @Transactional
    public TicketVO create(TicketCreateDTO createDTO) {
        TicketBO created = ticketManager.create(TicketConvert.fromCreateDTO(createDTO, LocalDateTime.now()));
        return TicketConvert.toVO(created);
    }

    @Override
    public TicketVO get(Long id) {
        return TicketConvert.toVO(ticketManager.getRequired(id));
    }

    @Override
    public TicketPageVO list(long page, long size, TicketStatus status) {
        return TicketConvert.toVO(ticketManager.page(page, size, status));
    }

    @Override
    @Transactional
    public TicketVO update(Long id, TicketUpdateDTO updateDTO) {
        TicketBO current = ticketManager.getRequired(id);
        if (updateDTO.status() != null && !current.status().canTransitionTo(updateDTO.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "工单状态不能从 " + current.status() + " 变更为 " + updateDTO.status());
        }
        TicketBO updated = ticketManager.update(TicketConvert.merge(current, updateDTO, LocalDateTime.now()));
        return TicketConvert.toVO(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ticketManager.delete(id);
    }
}
