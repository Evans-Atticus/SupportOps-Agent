package com.example.supportops.module.ticket.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.ticket.convert.TicketConvert;
import com.example.supportops.module.ticket.dao.dataobject.TicketDO;
import com.example.supportops.module.ticket.dao.mapper.TicketMapper;
import com.example.supportops.module.ticket.model.bo.TicketBO;
import com.example.supportops.module.ticket.model.bo.TicketPageBO;
import com.example.supportops.module.ticket.model.enums.TicketStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TicketManager {
    private final TicketMapper ticketMapper;

    public TicketManager(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    public TicketBO create(TicketBO ticket) {
        TicketDO dataObject = TicketConvert.toDO(ticket);
        try {
            ticketMapper.insert(dataObject);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "工单号已存在");
        }
        return TicketConvert.toBO(dataObject);
    }

    public TicketBO getRequired(Long id) {
        TicketDO dataObject = ticketMapper.selectById(id);
        if (dataObject == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return TicketConvert.toBO(dataObject);
    }

    /** 按对外工单号读取工单，诊断接口不暴露数据库主键。 */
    public TicketBO getRequiredByTicketNo(String ticketNo) {
        return findByTicketNo(ticketNo).orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    }

    public Optional<TicketBO> findByTicketNo(String ticketNo) {
        TicketDO dataObject = ticketMapper.selectOne(new LambdaQueryWrapper<TicketDO>()
                .eq(TicketDO::getTicketNo, ticketNo));
        return Optional.ofNullable(dataObject).map(TicketConvert::toBO);
    }

    public TicketPageBO page(long page, long size, TicketStatus status) {
        LambdaQueryWrapper<TicketDO> query = new LambdaQueryWrapper<TicketDO>()
                .eq(status != null, TicketDO::getStatus, status == null ? null : status.name())
                .orderByDesc(TicketDO::getCreatedAt);
        Page<TicketDO> result = ticketMapper.selectPage(Page.of(page, size), query);
        return new TicketPageBO(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(TicketConvert::toBO).toList());
    }

    public TicketBO update(TicketBO ticket) {
        TicketDO dataObject = TicketConvert.toDO(ticket);
        ticketMapper.updateById(dataObject);
        return TicketConvert.toBO(dataObject);
    }

    public void delete(Long id) {
        getRequired(id);
        ticketMapper.deleteById(id);
    }
}
