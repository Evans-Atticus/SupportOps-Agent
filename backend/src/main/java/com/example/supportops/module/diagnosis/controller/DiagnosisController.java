package com.example.supportops.module.diagnosis.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.diagnosis.application.DiagnosisApplicationService;
import com.example.supportops.module.diagnosis.model.dto.DiagnosisCreateDTO;
import com.example.supportops.module.diagnosis.model.vo.DiagnosisDetailVO;
import com.example.supportops.module.diagnosis.model.vo.DiagnosisTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 诊断 REST 接口层：负责协议适配、参数校验和统一响应封装，不包含业务规则。
 */
@RestController
@RequestMapping("/api/v1/diagnoses")
public class DiagnosisController {
    private final DiagnosisApplicationService diagnosisApplicationService;

    public DiagnosisController(DiagnosisApplicationService diagnosisApplicationService) {
        this.diagnosisApplicationService = diagnosisApplicationService;
    }

    /**
     * POST /api/v1/diagnoses：创建异步诊断任务并立即返回任务 ID。
     * @param body 工单号、可选业务号和可选场景
     * @param idempotencyKey 客户端重试标识；相同用户和键会复用原报告
     * @param authentication JWT 认证后得到的当前登录用户
     * @return PENDING 任务及建议轮询间隔
     */
    @Operation(summary = "创建异步 AI + 规则诊断任务")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DiagnosisTaskVO> diagnose(
            @Valid @RequestBody DiagnosisCreateDTO body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        // Authentication 由 JWT 过滤器建立，应用服务据此记录诊断发起人。
        String requestId = RequestIdFilter.getRequestId(request);
        return ApiResponse.success(diagnosisApplicationService.submit(
                body, authentication.getName(), idempotencyKey, requestId), requestId);
    }

    /** GET /api/v1/diagnoses/{diagnosisId}：按任务主键查询步骤与完整报告。 */
    @Operation(summary = "查询诊断任务和完整报告")
    @GetMapping("/{diagnosisId}")
    public ApiResponse<DiagnosisDetailVO> get(@PathVariable long diagnosisId, Authentication authentication,
                                              HttpServletRequest request) {
        return ApiResponse.success(diagnosisApplicationService.get(diagnosisId, authentication.getName()),
                RequestIdFilter.getRequestId(request));
    }

    /** GET /api/v1/diagnoses/{diagnosisId}/report：为前端提供语义更明确的报告地址。 */
    @Operation(summary = "查询报告详情（与任务详情返回同一聚合视图）")
    @GetMapping("/{diagnosisId}/report")
    public ApiResponse<DiagnosisDetailVO> report(@PathVariable long diagnosisId, Authentication authentication,
                                                 HttpServletRequest request) {
        // 当前版本任务详情就是完整报告聚合，因此复用同一个读取方法。
        return get(diagnosisId, authentication, request);
    }

    /** GET /api/v1/diagnoses：返回当前登录用户最近的诊断历史。 */
    @Operation(summary = "查询当前用户的诊断历史")
    @GetMapping
    public ApiResponse<java.util.List<com.example.supportops.module.diagnosis.model.vo.DiagnosisHistoryVO>> list(
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication,
            HttpServletRequest request) {
        return ApiResponse.success(diagnosisApplicationService.list(authentication.getName(), limit),
                RequestIdFilter.getRequestId(request));
    }

    /** POST apply：记录客服已采纳建议，诊断结论本身保持不可变。 */
    @Operation(summary = "采纳诊断建议")
    @PostMapping("/{diagnosisId}/apply")
    public ApiResponse<DiagnosisDetailVO> apply(@PathVariable long diagnosisId, Authentication authentication,
                                                HttpServletRequest request) {
        return ApiResponse.success(diagnosisApplicationService.apply(diagnosisId, authentication.getName()),
                RequestIdFilter.getRequestId(request));
    }

    /** POST discard：终止或丢弃当前诊断任务。 */
    @Operation(summary = "丢弃诊断任务")
    @PostMapping("/{diagnosisId}/discard")
    public ApiResponse<DiagnosisDetailVO> discard(@PathVariable long diagnosisId, Authentication authentication,
                                                  HttpServletRequest request) {
        return ApiResponse.success(diagnosisApplicationService.discard(diagnosisId, authentication.getName()),
                RequestIdFilter.getRequestId(request));
    }

    @Operation(summary = "上传并关联诊断附件")
    @PostMapping(path = "/{diagnosisId}/attachments", consumes = "multipart/form-data")
    public ApiResponse<List<Map<String, Object>>> uploadAttachments(
            @PathVariable long diagnosisId,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication,
            HttpServletRequest request) {
        return ApiResponse.success(diagnosisApplicationService.addAttachments(
                diagnosisId, authentication.getName(), files), RequestIdFilter.getRequestId(request));
    }
}
