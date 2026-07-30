package com.example.supportops.module.ticket.model.vo;

/** 导入结果明确说明是新建还是复用已同步工单，便于前端幂等处理。 */
public record TicketImportVO(TicketVO ticket, boolean created, String source) {
}
