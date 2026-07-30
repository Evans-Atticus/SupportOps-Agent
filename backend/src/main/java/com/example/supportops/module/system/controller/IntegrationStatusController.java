package com.example.supportops.module.system.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.system.model.vo.IntegrationStatusVO;
import com.example.supportops.module.system.service.IntegrationStatusService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationStatusController {
    private final IntegrationStatusService integrationStatusService;

    public IntegrationStatusController(IntegrationStatusService integrationStatusService) {
        this.integrationStatusService = integrationStatusService;
    }

    @Operation(summary = "查看外部平台连接器状态（不暴露凭证和地址）")
    @GetMapping("/status")
    public ApiResponse<IntegrationStatusVO> status(HttpServletRequest request) {
        return ApiResponse.success(integrationStatusService.status(), RequestIdFilter.getRequestId(request));
    }
}
