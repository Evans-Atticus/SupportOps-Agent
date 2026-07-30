SET NAMES utf8mb4;
USE supportops;

CREATE TABLE IF NOT EXISTS product_knowledge_documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_sku VARCHAR(64) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    source_reference VARCHAR(255) NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes BIGINT UNSIGNED NOT NULL,
    version_no VARCHAR(32) NOT NULL DEFAULT '1',
    index_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    checksum_sha256 CHAR(64) NOT NULL,
    extracted_text MEDIUMTEXT NULL,
    file_content LONGBLOB NOT NULL,
    error_message VARCHAR(500) NULL,
    synced_at DATETIME(3) NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_knowledge_checksum (product_sku, checksum_sha256),
    KEY idx_product_knowledge_sku_status (product_sku, index_status, updated_at),
    CONSTRAINT fk_product_knowledge_creator FOREIGN KEY (created_by) REFERENCES support_users (id),
    CONSTRAINT ck_product_knowledge_type CHECK
      (document_type IN ('PRODUCT_MANUAL','SPECIFICATION','USAGE_GUIDE','TROUBLESHOOTING','AFTER_SALES_SOP','FAQ','OTHER')),
    CONSTRAINT ck_product_knowledge_source CHECK (source_type IN ('ERP','MANUAL')),
    CONSTRAINT ck_product_knowledge_status CHECK (index_status IN ('PENDING','INDEXED','FAILED')),
    CONSTRAINT ck_product_knowledge_size CHECK (size_bytes <= 10485760)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_knowledge_chunks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NOT NULL,
    chunk_index INT UNSIGNED NOT NULL,
    chunk_text TEXT NOT NULL,
    character_count INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_knowledge_chunk (document_id, chunk_index),
    FULLTEXT KEY ft_product_knowledge_chunk (chunk_text),
    CONSTRAINT fk_product_knowledge_chunk_document FOREIGN KEY (document_id)
      REFERENCES product_knowledge_documents (id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO sop_definitions (scenario_type, title, audience, version, content_json, enabled)
VALUES
('PRODUCT_INFORMATION_QUERY', '产品规格与兼容性查询', '客服 / 客户', 1,
 JSON_ARRAY(
   JSON_OBJECT('order','1.','action','定位商品','tool','OrderQueryService','text','从客户所选订单确认商品 SKU，不依赖关键词猜测商品。','ruleExpression','order.productSku != null'),
   JSON_OBJECT('order','2.','action','检索资料','tool','ProductKnowledgeRetriever','text','仅检索该 SKU 已索引的规格、说明书与 FAQ 片段。','ruleExpression','document.indexStatus == INDEXED'),
   JSON_OBJECT('order','3.','action','依据回答','tool','VerifiedReplyBuilder','text','根据命中片段回答规格、参数与兼容性；资料未覆盖时明确说明。','ruleExpression',NULL)
 ), 1),
('PRODUCT_USAGE_GUIDANCE', '产品使用与保养指导', '客服 / 客户', 1,
 JSON_ARRAY(
   JSON_OBJECT('order','1.','action','定位商品','tool','OrderQueryService','text','从客户所选订单确认商品 SKU。','ruleExpression','order.productSku != null'),
   JSON_OBJECT('order','2.','action','检索指引','tool','ProductKnowledgeRetriever','text','检索该 SKU 的说明书、安装、使用和保养资料。','ruleExpression','document.indexStatus == INDEXED'),
   JSON_OBJECT('order','3.','action','安全回答','tool','VerifiedReplyBuilder','text','按资料给出可执行步骤，不补写资料中不存在的操作。','ruleExpression',NULL)
 ), 1),
('PRODUCT_TROUBLESHOOTING', '产品故障排查与售后指引', '客服 / 客户', 1,
 JSON_ARRAY(
   JSON_OBJECT('order','1.','action','理解现象','tool','TicketUnderstandingAI','text','识别客户描述的故障现象、触发条件和期望结果。','ruleExpression',NULL),
   JSON_OBJECT('order','2.','action','检索排障','tool','ProductKnowledgeRetriever','text','检索该 SKU 的故障排查、FAQ 与售后 SOP。','ruleExpression','document.indexStatus == INDEXED'),
   JSON_OBJECT('order','3.','action','分步指导','tool','VerifiedReplyBuilder','text','依据资料给出安全排查步骤；涉及拆机、用电或安全风险时停止自行操作并转人工。','ruleExpression',NULL)
 ), 1)
ON DUPLICATE KEY UPDATE title=VALUES(title), audience=VALUES(audience), content_json=VALUES(content_json), enabled=1;
