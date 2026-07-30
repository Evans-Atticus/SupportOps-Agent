package com.example.supportops.module.portal.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.portal.model.dto.RefundCreateDTO;
import com.example.supportops.module.portal.model.dto.HandoffRequestDTO;
import com.example.supportops.module.portal.model.vo.PortalModels.DashboardVO;
import com.example.supportops.module.portal.model.vo.PortalModels.AdviceVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ItemVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ExportVO;
import com.example.supportops.module.portal.model.vo.PortalModels.RefundVO;
import com.example.supportops.module.portal.model.vo.PortalModels.SearchResultVO;
import com.example.supportops.module.portal.model.vo.PortalModels.HandoffResultVO;
import com.example.supportops.module.portal.model.vo.PortalModels.CustomerConversationVO;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerPortalController {
    private final PortalService service;

    public CustomerPortalController(PortalService service) {
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
        return ApiResponse.success(service.search(authentication.getName(), customerModule(module), keyword),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/refunds")
    public ApiResponse<List<RefundVO>> refunds(@RequestParam(defaultValue = "") String keyword,
                                               Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.refunds(authentication.getName(), keyword),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/modules/{module}/export")
    public ApiResponse<ExportVO> exportModule(@PathVariable String module,
                                              @RequestParam(defaultValue = "") String keyword,
                                              Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.exportModule(authentication.getName(), customerModule(module), keyword),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/modules/{module}/items/{id}")
    public ApiResponse<ItemVO> moduleItem(@PathVariable String module, @PathVariable String id,
                                         Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.moduleItem(authentication.getName(), customerModule(module), id),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/modules/{module}/advice")
    public ApiResponse<AdviceVO> advice(@PathVariable String module, Authentication authentication,
                                        HttpServletRequest request) {
        return ApiResponse.success(service.advice(authentication.getName(), customerModule(module)),
                RequestIdFilter.getRequestId(request));
    }

    @PostMapping("/refunds")
    public ApiResponse<RefundVO> createRefund(@Valid @RequestBody RefundCreateDTO body,
                                              Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(service.createRefund(authentication.getName(), body,
                        RequestIdFilter.getRequestId(request)), RequestIdFilter.getRequestId(request));
    }

    @PostMapping("/conversations/handoff")
    public ApiResponse<HandoffResultVO> handoff(@Valid @RequestBody HandoffRequestDTO body,
                                                Authentication authentication,
                                                HttpServletRequest request) {
        return ApiResponse.success(service.requestHandoff(authentication.getName(), body),
                RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/conversations/{conversationNo}")
    public ApiResponse<CustomerConversationVO> conversation(@PathVariable String conversationNo,
                                                             Authentication authentication,
                                                             HttpServletRequest request) {
        return ApiResponse.success(service.customerConversation(authentication.getName(), conversationNo),
                RequestIdFilter.getRequestId(request));
    }

    @PostMapping(value = "/conversations/{conversationNo}/messages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CustomerConversationVO> sendMessage(
            @PathVariable String conversationNo,
            @RequestParam(defaultValue = "") String content,
            @RequestParam(required = false) String ticketNo,
            @RequestParam(required = false) String businessNo,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication,
            HttpServletRequest request) {
        return ApiResponse.success(service.sendCustomerConversationMessage(
                        authentication.getName(), conversationNo, content,
                        ticketNo, businessNo, files),
                RequestIdFilter.getRequestId(request));
    }

    @DeleteMapping("/conversations/{conversationNo}/messages/{messageId}")
    public ApiResponse<CustomerConversationVO> recallMessage(@PathVariable String conversationNo,
                                                              @PathVariable long messageId,
                                                              Authentication authentication,
                                                              HttpServletRequest request) {
        return ApiResponse.success(service.recallCustomerMessage(
                        authentication.getName(), conversationNo, messageId),
                RequestIdFilter.getRequestId(request));
    }

    private String customerModule(String module) {
        return switch (module) {
            case "my-orders" -> "orders";
            case "my-logistics" -> "logistics";
            case "after-sales" -> "tickets";
            default -> module;
        };
    }
}
