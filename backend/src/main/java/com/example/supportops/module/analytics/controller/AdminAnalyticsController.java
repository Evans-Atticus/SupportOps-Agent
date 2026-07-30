package com.example.supportops.module.analytics.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.analytics.model.ServiceOperationsAnalyticsModels.SnapshotVO;
import com.example.supportops.module.analytics.service.ServiceOperationsAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {
    private final ServiceOperationsAnalyticsService service;

    public AdminAnalyticsController(ServiceOperationsAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/service-operations")
    public ApiResponse<SnapshotVO> serviceOperations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "") String channel,
            @RequestParam(defaultValue = "") String priority,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request
    ) {
        return ApiResponse.success(service.snapshot(from, to, channel, priority, status),
                RequestIdFilter.getRequestId(request));
    }
}
