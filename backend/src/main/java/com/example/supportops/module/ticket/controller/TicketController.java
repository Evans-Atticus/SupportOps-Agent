package com.example.supportops.module.ticket.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.ticket.model.dto.TicketCreateDTO;
import com.example.supportops.module.ticket.model.dto.TicketUpdateDTO;
import com.example.supportops.module.ticket.model.enums.TicketStatus;
import com.example.supportops.module.ticket.model.vo.TicketPageVO;
import com.example.supportops.module.ticket.model.vo.TicketVO;
import com.example.supportops.module.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "创建工单")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TicketVO> create(@Valid @RequestBody TicketCreateDTO body, HttpServletRequest request) {
        return ApiResponse.success(ticketService.create(body), RequestIdFilter.getRequestId(request));
    }

    @Operation(summary = "查询工单")
    @GetMapping("/{id}")
    public ApiResponse<TicketVO> get(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(ticketService.get(id), RequestIdFilter.getRequestId(request));
    }

    @Operation(summary = "分页查询工单")
    @GetMapping
    public ApiResponse<TicketPageVO> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) TicketStatus status,
            HttpServletRequest request) {
        return ApiResponse.success(ticketService.list(page, size, status), RequestIdFilter.getRequestId(request));
    }

    @Operation(summary = "更新工单")
    @PutMapping("/{id}")
    public ApiResponse<TicketVO> update(@PathVariable Long id, @Valid @RequestBody TicketUpdateDTO body,
                                        HttpServletRequest request) {
        return ApiResponse.success(ticketService.update(id, body), RequestIdFilter.getRequestId(request));
    }

    @Operation(summary = "删除工单")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        ticketService.delete(id);
        return ApiResponse.success(null, RequestIdFilter.getRequestId(request));
    }
}
