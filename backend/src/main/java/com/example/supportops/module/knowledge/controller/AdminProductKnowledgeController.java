package com.example.supportops.module.knowledge.controller;

import com.example.supportops.common.response.ApiResponse;
import com.example.supportops.infrastructure.web.RequestIdFilter;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeDocument;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeFile;
import com.example.supportops.module.knowledge.service.ProductKnowledgeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductKnowledgeController {
    private final ProductKnowledgeService productKnowledgeService;

    public AdminProductKnowledgeController(ProductKnowledgeService productKnowledgeService) {
        this.productKnowledgeService = productKnowledgeService;
    }

    @GetMapping("/orders/{orderNo}/knowledge-documents")
    public ApiResponse<List<ProductKnowledgeDocument>> productDocuments(@PathVariable String orderNo,
                                                                         Authentication authentication,
                                                                         HttpServletRequest request) {
        return ApiResponse.success(productKnowledgeService.listForOrder(authentication.getName(), orderNo),
                RequestIdFilter.getRequestId(request));
    }

    @PostMapping(value = "/orders/{orderNo}/knowledge-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductKnowledgeDocument> uploadProductDocument(
            @PathVariable String orderNo,
            @RequestParam(defaultValue = "OTHER") String documentType,
            @RequestParam(defaultValue = "MANUAL") String sourceType,
            @RequestParam(required = false) String sourceReference,
            @RequestParam(defaultValue = "1") String version,
            @RequestPart("file") MultipartFile file,
            Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(productKnowledgeService.upload(authentication.getName(), orderNo, documentType,
                        sourceType, sourceReference, version, file), RequestIdFilter.getRequestId(request));
    }

    @GetMapping("/orders/{orderNo}/knowledge-documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadProductDocument(@PathVariable String orderNo,
                                                           @PathVariable long documentId,
                                                           Authentication authentication) {
        ProductKnowledgeFile file = productKnowledgeService.download(authentication.getName(), orderNo, documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/orders/{orderNo}/knowledge-documents/{documentId}")
    public ApiResponse<Void> deleteProductDocument(@PathVariable String orderNo,
                                                    @PathVariable long documentId,
                                                    Authentication authentication,
                                                    HttpServletRequest request) {
        productKnowledgeService.delete(authentication.getName(), orderNo, documentId);
        return ApiResponse.success(null, RequestIdFilter.getRequestId(request));
    }
}
