package com.example.supportops.module.integration.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.integration.model.ExternalSyncModels.SyncReservationVO;
import com.example.supportops.module.integration.service.ExternalSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/integrations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExternalSyncController {
    private final ExternalSyncService externalSyncService;

    public AdminExternalSyncController(ExternalSyncService externalSyncService) {
        this.externalSyncService = externalSyncService;
    }

    @PostMapping("/erp/sync/orders-tickets")
    public ApiResponse<SyncReservationVO> syncErpOrdersAndTickets(
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(
                externalSyncService.syncErpOrdersAndTickets(authentication.getName()),
                RequestIdFilter.getRequestId(request)
        );
    }

    @PostMapping("/wms/sync/logistics")
    public ApiResponse<SyncReservationVO> syncWmsLogistics(
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(
                externalSyncService.syncWmsLogistics(authentication.getName()),
                RequestIdFilter.getRequestId(request)
        );
    }

}
