package com.example.supportops.module.ticket.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.ticket.model.vo.TicketImportVO;
import com.example.supportops.module.ticket.service.TicketIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/integrations/tickets")
public class TicketIntegrationController {
    private final TicketIntegrationService integrationService;

    public TicketIntegrationController(TicketIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @Operation(summary = "从外部工单平台导入工单和业务号")
    @PostMapping("/{ticketNo}/import")
    public ApiResponse<TicketImportVO> importTicket(@PathVariable @Size(max = 40) String ticketNo,
                                                     HttpServletRequest request) {
        return ApiResponse.success(integrationService.importTicket(ticketNo), RequestIdFilter.getRequestId(request));
    }
}
