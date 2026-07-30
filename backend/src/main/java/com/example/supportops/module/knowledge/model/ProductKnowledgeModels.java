package com.example.supportops.module.knowledge.model;

import java.time.LocalDateTime;

public final class ProductKnowledgeModels {
    private ProductKnowledgeModels() {
    }

    public record ProductReference(String orderNo, String productSku, String productName) {
    }

    public record ProductKnowledgeDocument(
            long id, String productSku, String documentType, String sourceType, String sourceReference,
            String originalName, String contentType, long sizeBytes, String version,
            String indexStatus, String preview, String errorMessage, LocalDateTime syncedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
    }

    public record ProductKnowledgeFile(long id, String originalName, String contentType, byte[] content) {
    }

    public record ProductKnowledgeSnippet(
            long documentId, long chunkId, String productSku, String documentType,
            String documentName, int chunkIndex, String content, double relevance
    ) {
    }

    public record StoredChunk(long chunkId, long documentId, String productSku, String documentType,
                              String documentName, int chunkIndex, String content) {
    }
}
