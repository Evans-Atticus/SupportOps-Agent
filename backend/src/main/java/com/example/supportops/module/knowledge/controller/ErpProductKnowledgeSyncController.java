package com.example.supportops.module.knowledge.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeDocument;
import com.example.supportops.module.knowledge.service.ProductKnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/integrations")
@PreAuthorize("hasRole('ADMIN')")
public class ErpProductKnowledgeSyncController {
    private final ProductKnowledgeService productKnowledgeService;

    public ErpProductKnowledgeSyncController(ProductKnowledgeService productKnowledgeService) {
        this.productKnowledgeService = productKnowledgeService;
    }

    /**
     * ERP 产品知识同步适配入口。当前可用 multipart 模拟 ERP 推送；接入真实 ERP 后，
     * 网关只需按相同字段调用此契约，解析、切片和 RAG 索引逻辑无需改变。
     */
    @PostMapping(value = "/erp/sync/product-knowledge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductKnowledgeDocument> syncErpProductKnowledge(
            @RequestParam String orderNo,
            @RequestParam(defaultValue = "OTHER") String documentType,
            @RequestParam String sourceReference,
            @RequestParam(defaultValue = "1") String version,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest request) {
        return ApiResponse.success(productKnowledgeService.syncFromErp(authentication.getName(), orderNo,
                        documentType, sourceReference, version, file),
                RequestIdFilter.getRequestId(request));
    }
}
