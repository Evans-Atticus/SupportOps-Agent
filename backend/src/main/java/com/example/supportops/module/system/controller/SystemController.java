package com.example.supportops.module.system.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.system.model.vo.HealthVO;
import com.example.supportops.module.system.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @Operation(summary = "服务健康检查")
    @GetMapping("/health")
    public ApiResponse<HealthVO> health(HttpServletRequest request) {
        return ApiResponse.success(systemService.health(), RequestIdFilter.getRequestId(request));
    }
}
