package com.example.supportops.module.knowledge.service;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.knowledge.dao.ProductKnowledgeDAO;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeDocument;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeFile;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeSnippet;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductReference;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.StoredChunk;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProductKnowledgeService {
    private static final Set<String> DOCUMENT_TYPES = Set.of("PRODUCT_MANUAL", "SPECIFICATION", "USAGE_GUIDE",
            "TROUBLESHOOTING", "AFTER_SALES_SOP", "FAQ", "OTHER");
    private static final Set<String> SOURCE_TYPES = Set.of("ERP", "MANUAL");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 200_000;
    private static final int CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 100;
    private static final Pattern ASCII_TERM = Pattern.compile("[a-z0-9][a-z0-9._+-]{1,}");

    private final ProductKnowledgeDAO dao;
    private final Tika tika = new Tika();

    public ProductKnowledgeService(ProductKnowledgeDAO dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public List<ProductKnowledgeDocument> listForOrder(String username, String orderNo) {
        requireAdmin(username);
        return dao.listDocuments(requiredProduct(orderNo).productSku());
    }

    @Transactional
    public ProductKnowledgeDocument upload(String username, String orderNo, String documentType,
                                           String sourceType, String sourceReference, String version,
                                           MultipartFile file) {
        long administratorId = requireAdmin(username);
        ProductReference product = requiredProduct(orderNo);
        String safeDocumentType = normalize(documentType, "OTHER");
        String safeSourceType = normalize(sourceType, "MANUAL");
        if (!DOCUMENT_TYPES.contains(safeDocumentType) || !SOURCE_TYPES.contains(safeSourceType)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的文档或来源类型");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择不超过 10MB 的产品资料");
        }
        String originalName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "无法读取产品资料：" + originalName);
        }
        String extracted;
        try {
            Metadata metadata = new Metadata();
            metadata.set("resourceName", originalName);
            extracted = normalizeText(tika.parseToString(new ByteArrayInputStream(bytes), metadata, MAX_TEXT_LENGTH));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文档无法解析，请上传 PDF、Word、TXT、Markdown 或常用办公文档");
        }
        if (extracted.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "文档中没有可供检索的文本内容");
        }
        long id;
        try {
            id = dao.insertDocument(product.productSku(), safeDocumentType, safeSourceType,
                    blankToNull(sourceReference), originalName, contentType, bytes,
                    normalizeVersion(version), sha256(bytes), extracted, "INDEXED", null,
                    administratorId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "该 SKU 已存在内容相同的附件");
        }
        List<String> chunks = chunk(extracted);
        for (int i = 0; i < chunks.size(); i++) dao.insertChunk(id, i, chunks.get(i));
        return dao.listDocuments(product.productSku()).stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * ERP/PIM/SOP 模块统一通过此入口同步产品资料。来源由服务端固定为 ERP，
     * 调用方不能把普通管理员上传伪装为外部系统同步。
     */
    @Transactional
    public ProductKnowledgeDocument syncFromErp(String username, String orderNo, String documentType,
                                                 String sourceReference, String version, MultipartFile file) {
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ERP 文档编号不能为空");
        }
        return upload(username, orderNo, documentType, "ERP", sourceReference, version, file);
    }

    @Transactional(readOnly = true)
    public ProductKnowledgeFile download(String username, String orderNo, long documentId) {
        requireAdmin(username);
        ProductKnowledgeFile file = dao.findFile(documentId, requiredProduct(orderNo).productSku());
        if (file == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "产品附件不存在");
        return file;
    }

    @Transactional
    public void delete(String username, String orderNo, long documentId) {
        requireAdmin(username);
        if (dao.deleteDocument(documentId, requiredProduct(orderNo).productSku()) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "产品附件不存在");
        }
    }

    @Transactional(readOnly = true)
    public long countForOrder(String orderNo) {
        ProductReference product = requiredProduct(orderNo);
        return dao.countDocuments(product.productSku());
    }

    @Transactional(readOnly = true)
    public List<ProductKnowledgeSnippet> retrieveForOrder(String orderNo, String question, int topK) {
        ProductReference product = requiredProduct(orderNo);
        List<String> terms = terms(question);
        return dao.selectChunks(product.productSku()).stream()
                .map(chunk -> scored(chunk, terms))
                .filter(hit -> hit.relevance() > 0 || terms.isEmpty())
                .sorted(Comparator.comparingDouble(ProductKnowledgeSnippet::relevance).reversed()
                        .thenComparingLong(ProductKnowledgeSnippet::documentId)
                        .thenComparingInt(ProductKnowledgeSnippet::chunkIndex))
                .limit(Math.max(1, Math.min(topK, 8)))
                .toList();
    }

    private ProductKnowledgeSnippet scored(StoredChunk chunk, List<String> terms) {
        String body = chunk.content().toLowerCase(Locale.ROOT);
        double score = 0;
        for (String term : terms) {
            int count = occurrences(body, term);
            if (count > 0) score += (term.length() >= 3 ? 2.0 : 1.0) * Math.min(count, 4);
        }
        return new ProductKnowledgeSnippet(chunk.documentId(), chunk.chunkId(), chunk.productSku(),
                chunk.documentType(), chunk.documentName(), chunk.chunkIndex(), chunk.content(), score);
    }

    private List<String> terms(String question) {
        String value = question == null ? "" : question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Matcher matcher = ASCII_TERM.matcher(value);
        while (matcher.find()) values.add(matcher.group());
        String chinese = value.replaceAll("[^\\p{IsHan}]", "");
        for (int i = 0; i + 1 < chinese.length(); i++) values.add(chinese.substring(i, i + 2));
        Set<String> stop = Set.of("这个", "那个", "产品", "商品", "请问", "一下", "怎么", "如何", "可以", "是否");
        values.removeIf(stop::contains);
        return List.copyOf(values);
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        String[] semanticSections = text.split("(?m)(?=^#{1,6}\\s+)");
        if (semanticSections.length > 1) {
            for (String section : semanticSections) addSizedChunks(chunks, section.trim());
            return chunks;
        }
        addSizedChunks(chunks, text);
        return chunks;
    }

    private void addSizedChunks(List<String> chunks, String text) {
        if (text == null || text.isBlank()) return;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('。', end));
                if (boundary > start + CHUNK_SIZE / 2) end = boundary + 1;
            }
            String value = text.substring(start, end).trim();
            if (!value.isBlank()) chunks.add(value);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
    }

    private ProductReference requiredProduct(String orderNo) {
        ProductReference product = dao.findProductByOrder(orderNo);
        if (product == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在");
        if (product.productSku().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "订单尚未关联产品 SKU，无法管理产品知识附件");
        }
        return product;
    }

    private long requireAdmin(String username) {
        Long administratorId = dao.findAdminUserId(username);
        if (administratorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有系统管理员可以管理产品知识附件");
        }
        return administratorId;
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        String normalized = value.replace('\u0000', ' ').replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("[ ]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH);
    }

    private int occurrences(String text, String term) {
        int count = 0;
        for (int index = text.indexOf(term); index >= 0; index = text.indexOf(term, index + term.length())) count++;
        return count;
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(64);
            for (byte item : hash) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String safeFileName(String value) {
        String name = value == null ? "product-document" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.isBlank() ? "product-document" : name.substring(0, Math.min(255, name.length()));
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeVersion(String value) {
        String version = value == null || value.isBlank() ? "1" : value.trim();
        return version.substring(0, Math.min(32, version.length()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
