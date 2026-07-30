package com.example.supportops.module.ticket.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.supportops.module.ticket.dao.dataobject.TicketDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<TicketDO> {
}
