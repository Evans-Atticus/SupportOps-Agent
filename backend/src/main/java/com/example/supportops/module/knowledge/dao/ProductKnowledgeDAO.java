package com.example.supportops.module.knowledge.dao;

import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeDocument;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductKnowledgeFile;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.ProductReference;
import com.example.supportops.module.knowledge.model.ProductKnowledgeModels.StoredChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ProductKnowledgeDAO {
    private final JdbcTemplate jdbcTemplate;

    public ProductKnowledgeDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long findAdminUserId(String username) {
        return jdbcTemplate.query("""
                SELECT id FROM support_users
                 WHERE username=? AND role_code='ADMIN' AND status='ACTIVE'
                """, rs -> rs.next() ? rs.getLong("id") : null, username);
    }

    public ProductReference findProductByOrder(String orderNo) {
        return jdbcTemplate.query("""
                SELECT order_no, COALESCE(product_scope, '') AS product_scope
                  FROM biz_orders WHERE order_no=?
                """, rs -> {
            if (!rs.next()) return null;
            String scope = rs.getString("product_scope").trim();
            String[] parts = scope.split("\\s+", 2);
            String sku = parts.length == 0 ? "" : parts[0];
            String name = parts.length > 1 ? parts[1] : scope;
            return new ProductReference(rs.getString("order_no"), sku, name);
        }, orderNo);
    }

    public List<ProductKnowledgeDocument> listDocuments(String productSku) {
        return jdbcTemplate.query("""
                SELECT id, product_sku, document_type, source_type, source_reference, original_name,
                       content_type, size_bytes, version_no, index_status,
                       LEFT(COALESCE(extracted_text,''), 360) AS preview, error_message,
                       synced_at, created_at, updated_at
                  FROM product_knowledge_documents
                 WHERE product_sku=? ORDER BY updated_at DESC, id DESC
                """, (rs, rowNum) -> new ProductKnowledgeDocument(
                rs.getLong("id"), rs.getString("product_sku"), rs.getString("document_type"),
                rs.getString("source_type"), rs.getString("source_reference"), rs.getString("original_name"),
                rs.getString("content_type"), rs.getLong("size_bytes"), rs.getString("version_no"),
                rs.getString("index_status"), rs.getString("preview"), rs.getString("error_message"),
                time(rs.getTimestamp("synced_at")), time(rs.getTimestamp("created_at")),
                time(rs.getTimestamp("updated_at"))), productSku);
    }

    public long insertDocument(String sku, String documentType, String sourceType, String sourceReference,
                               String originalName, String contentType, byte[] content, String version,
                               String checksum, String extractedText, String status, String errorMessage,
                               long createdBy) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO product_knowledge_documents
                      (product_sku, document_type, source_type, source_reference, original_name, content_type,
                       size_bytes, version_no, index_status, checksum_sha256, extracted_text, file_content,
                       error_message, synced_at, created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ?='ERP' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, sku);
            statement.setString(2, documentType);
            statement.setString(3, sourceType);
            statement.setString(4, sourceReference);
            statement.setString(5, originalName);
            statement.setString(6, contentType);
            statement.setLong(7, content.length);
            statement.setString(8, version);
            statement.setString(9, status);
            statement.setString(10, checksum);
            statement.setString(11, extractedText);
            statement.setBytes(12, content);
            statement.setString(13, errorMessage);
            statement.setString(14, sourceType);
            statement.setLong(15, createdBy);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void insertChunk(long documentId, int index, String content) {
        jdbcTemplate.update("""
                INSERT INTO product_knowledge_chunks(document_id, chunk_index, chunk_text, character_count)
                VALUES (?,?,?,?)
                """, documentId, index, content, content.length());
    }

    public List<StoredChunk> selectChunks(String productSku) {
        return jdbcTemplate.query("""
                SELECT c.id AS chunk_id, d.id AS document_id, d.product_sku, d.document_type,
                       d.original_name, c.chunk_index, c.chunk_text
                  FROM product_knowledge_chunks c
                  JOIN product_knowledge_documents d ON d.id=c.document_id
                 WHERE d.product_sku=? AND d.index_status='INDEXED'
                 ORDER BY d.updated_at DESC, d.id DESC, c.chunk_index
                 LIMIT 300
                """, (rs, rowNum) -> new StoredChunk(
                rs.getLong("chunk_id"), rs.getLong("document_id"), rs.getString("product_sku"),
                rs.getString("document_type"), rs.getString("original_name"), rs.getInt("chunk_index"),
                rs.getString("chunk_text")), productSku);
    }

    public ProductKnowledgeFile findFile(long documentId, String productSku) {
        return jdbcTemplate.query("""
                SELECT id, original_name, content_type, file_content
                  FROM product_knowledge_documents WHERE id=? AND product_sku=?
                """, rs -> rs.next() ? new ProductKnowledgeFile(rs.getLong("id"), rs.getString("original_name"),
                rs.getString("content_type"), rs.getBytes("file_content")) : null, documentId, productSku);
    }

    public int deleteDocument(long documentId, String productSku) {
        return jdbcTemplate.update("DELETE FROM product_knowledge_documents WHERE id=? AND product_sku=?",
                documentId, productSku);
    }

    public long countDocuments(String productSku) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_knowledge_documents WHERE product_sku=?", Long.class, productSku);
        return value == null ? 0 : value;
    }

    private static LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
