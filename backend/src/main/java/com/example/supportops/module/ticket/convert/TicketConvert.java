package com.example.supportops.module.ticket.convert;

import com.example.supportops.module.ticket.dao.dataobject.TicketDO;
import com.example.supportops.module.ticket.model.bo.TicketBO;
import com.example.supportops.module.ticket.model.bo.TicketPageBO;
import com.example.supportops.module.ticket.model.dto.TicketCreateDTO;
import com.example.supportops.module.ticket.model.dto.TicketUpdateDTO;
import com.example.supportops.module.ticket.model.enums.TicketPriority;
import com.example.supportops.module.ticket.model.enums.TicketStatus;
import com.example.supportops.module.ticket.model.vo.TicketPageVO;
import com.example.supportops.module.ticket.model.vo.TicketVO;

import java.time.LocalDateTime;

public final class TicketConvert {
    private TicketConvert() {
    }

    public static TicketBO fromCreateDTO(TicketCreateDTO source, LocalDateTime now) {
        return new TicketBO(null, source.ticketNo(), source.customerId(), source.businessNo(), source.channel(),
                source.description(), null, TicketStatus.OPEN,
                source.priority() != null ? source.priority() : TicketPriority.NORMAL, now, now);
    }

    public static TicketBO merge(TicketBO current, TicketUpdateDTO source, LocalDateTime now) {
        return new TicketBO(current.id(), current.ticketNo(), current.customerId(),
                source.businessNo() != null ? source.businessNo() : current.businessNo(),
                source.channel() != null ? source.channel() : current.channel(),
                source.description() != null ? source.description() : current.description(),
                current.scenarioHint(), source.status() != null ? source.status() : current.status(),
                source.priority() != null ? source.priority() : current.priority(), current.createdAt(), now);
    }

    public static TicketDO toDO(TicketBO source) {
        TicketDO target = new TicketDO();
        target.setId(source.id());
        target.setTicketNo(source.ticketNo());
        target.setCustomerId(source.customerId());
        target.setBusinessNo(source.businessNo());
        target.setChannel(source.channel());
        target.setDescription(source.description());
        target.setScenarioHint(source.scenarioHint());
        target.setStatus(source.status().name());
        target.setPriority(source.priority().name());
        target.setCreatedAt(source.createdAt());
        target.setUpdatedAt(source.updatedAt());
        return target;
    }

    public static TicketBO toBO(TicketDO source) {
        return new TicketBO(source.getId(), source.getTicketNo(), source.getCustomerId(), source.getBusinessNo(),
                source.getChannel(), source.getDescription(), source.getScenarioHint(),
                TicketStatus.valueOf(source.getStatus()), TicketPriority.valueOf(source.getPriority()),
                source.getCreatedAt(), source.getUpdatedAt());
    }

    public static TicketVO toVO(TicketBO source) {
        return new TicketVO(source.id(), source.ticketNo(), source.customerId(), source.businessNo(), source.channel(),
                source.description(), source.scenarioHint(), source.status(), source.priority(), source.createdAt(),
                source.updatedAt());
    }

    public static TicketPageVO toVO(TicketPageBO source) {
        return new TicketPageVO(source.page(), source.size(), source.total(),
                source.records().stream().map(TicketConvert::toVO).toList());
    }
}
