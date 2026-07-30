package com.example.supportops.module.portal.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.portal.model.dto.RefundDecisionDTO;
import com.example.supportops.module.portal.model.dto.SupportUserCreateDTO;
import com.example.supportops.module.portal.model.dto.SupportUserUpdateDTO;
import com.example.supportops.module.portal.model.vo.PortalModels.DashboardVO;
import com.example.supportops.module.portal.model.vo.PortalModels.AdviceVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ExportVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ItemVO;
import com.example.supportops.module.portal.model.vo.PortalModels.OffboardResultVO;
import com.example.supportops.module.portal.model.vo.PortalModels.RefundVO;
import com.example.supportops.module.portal.model.vo.PortalModels.SearchResultVO;
import com.example.supportops.module.portal.service.PortalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPortalController {
    private final PortalService service;

    public AdminPortalController(PortalService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardVO> dashboard(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.dashboard(authentication.getName()), RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/modules/{module}")
    public ApiResponse<SearchResultVO> module(@PathVariable String module,
                                              @RequestParam(defaultValue = "") String keyword,
                                              Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.search(authentication.getName(), adminModule(module), keyword),
                RequestIdFilter.getRequestId(request));
    }

    @PostMapping("/people")
    public ApiResponse<ItemVO> createSupportUser(@Valid @RequestBody SupportUserCreateDTO body,
                                                 Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.createSupportUser(authentication.getName(), body),
                RequestIdFilter.getRequestId(request));
    }

    @PutMapping("/people/{userId}")
    public ApiResponse<ItemVO> updateSupportUser(@PathVariable long userId,
                                                 @Valid @RequestBody SupportUserUpdateDTO body,
                                                 Authentication authentication, HttpServletRequest request) {
        String requestId = RequestIdFilter.getRequestId(request);
        return ApiResponse.success(service.updateSupportUser(authentication.getName(), userId, body, requestId),
                requestId);
    }

    @DeleteMapping("/people/{userId}")
    public ApiResponse<Void> deleteSupportUser(@PathVariable long userId,
                                               Authentication authentication,
                                               HttpServletRequest request) {
        String requestId = RequestIdFilter.getRequestId(request);
        service.deleteSupportUser(authentication.getName(), userId, requestId);
        return ApiResponse.success(null, requestId);
    }

    @PostMapping("/people/{userId}/offboard")
    public ApiResponse<OffboardResultVO> offboardSupportUser(@PathVariable long userId,
                                                             Authentication authentication,
                                                             HttpServletRequest request) {
        String requestId = RequestIdFilter.getRequestId(request);
        return ApiResponse.success(service.offboardSupportUser(authentication.getName(), userId, requestId),
                requestId);
    }

    @GetMapping("/modules/{module}/export")
    public ApiResponse<ExportVO> exportModule(@PathVariable String module,
                                              @RequestParam(defaultValue = "") String keyword,
                                              Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.exportModule(authentication.getName(), adminModule(module), keyword),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/modules/{module}/items/{id}")
    public ApiResponse<ItemVO> moduleItem(@PathVariable String module, @PathVariable String id,
                                         Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.moduleItem(authentication.getName(), adminModule(module), id),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/modules/{module}/advice")
    public ApiResponse<AdviceVO> advice(@PathVariable String module, Authentication authentication,
                                        HttpServletRequest request) {
        return ApiResponse.success(service.advice(authentication.getName(), adminModule(module)),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/refunds")
    public ApiResponse<List<RefundVO>> refunds(@RequestParam(defaultValue = "") String keyword,
                                               Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.refunds(authentication.getName(), keyword),
                RequestIdFilter.getRequestId(request));
    }

    @PostMapping("/refunds/{refundNo}/approve")
    public ApiResponse<RefundVO> approve(@PathVariable String refundNo,
                                         @Valid @RequestBody RefundDecisionDTO body,
                                         Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.approve(authentication.getName(), refundNo, body,
                        RequestIdFilter.getRequestId(request)), RequestIdFilter.getRequestId(request));
    }

    @PostMapping("/refunds/{refundNo}/reject")
    public ApiResponse<RefundVO> reject(@PathVariable String refundNo,
                                        @Valid @RequestBody RefundDecisionDTO body,
                                        Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.reject(authentication.getName(), refundNo, body,
                        RequestIdFilter.getRequestId(request)), RequestIdFilter.getRequestId(request));
    }

    @PostMapping("/refunds/{refundNo}/execute")
    public ApiResponse<RefundVO> execute(@PathVariable String refundNo,
                                         Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.execute(authentication.getName(), refundNo,
                        RequestIdFilter.getRequestId(request)), RequestIdFilter.getRequestId(request));
    }

    private String adminModule(String module) {
        return "refund-approval".equals(module) ? "refunds" : module;
    }
}
