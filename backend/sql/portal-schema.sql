SET NAMES utf8mb4;
USE supportops;

CREATE TABLE IF NOT EXISTS customer_accounts (
    user_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_customer_accounts_customer (customer_id),
    CONSTRAINT fk_customer_accounts_user FOREIGN KEY (user_id) REFERENCES support_users (id),
    CONSTRAINT fk_customer_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB;

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

CREATE TABLE IF NOT EXISTS support_conversations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_no VARCHAR(64) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    assigned_user_id BIGINT UNSIGNED NULL,
    related_ticket_id BIGINT UNSIGNED NULL,
    related_diagnosis_id BIGINT UNSIGNED NULL,
    service_mode VARCHAR(24) NOT NULL DEFAULT 'BOT_SERVING',
    summary VARCHAR(500) NULL,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    agent_archived_at DATETIME(3) NULL,
    last_message_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_conversations_no (conversation_no),
    KEY idx_support_conversations_customer (customer_id, last_message_at),
    KEY idx_support_conversations_assignee (assigned_user_id, service_mode, last_message_at),
    KEY idx_support_conversations_diagnosis (related_diagnosis_id),
    CONSTRAINT fk_support_conversations_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_support_conversations_assignee FOREIGN KEY (assigned_user_id) REFERENCES support_users (id),
    CONSTRAINT fk_support_conversations_ticket FOREIGN KEY (related_ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_support_conversations_diagnosis FOREIGN KEY (related_diagnosis_id) REFERENCES diagnosis_tasks (id),
    CONSTRAINT ck_support_conversations_mode CHECK
      (service_mode IN ('BOT_SERVING','WAITING_AGENT','AGENT_SERVING','WAITING_CUSTOMER','ENDED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    sender_user_id BIGINT UNSIGNED NULL,
    content VARCHAR(2000) NOT NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_conversation_messages_conversation (conversation_id, sent_at),
    CONSTRAINT fk_conversation_messages_conversation FOREIGN KEY (conversation_id)
      REFERENCES support_conversations (id),
    CONSTRAINT fk_conversation_messages_sender FOREIGN KEY (sender_user_id) REFERENCES support_users (id),
    CONSTRAINT ck_conversation_messages_sender CHECK
      (sender_type IN ('CUSTOMER','BOT','SUPPORT_AGENT','SYSTEM'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversation_attachments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes BIGINT UNSIGNED NOT NULL,
    content LONGBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_conversation_attachments_message (message_id),
    CONSTRAINT fk_conversation_attachments_message FOREIGN KEY (message_id)
      REFERENCES conversation_messages (id) ON DELETE CASCADE,
    CONSTRAINT ck_conversation_attachments_size CHECK (size_bytes <= 5242880)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NULL,
    requested_by BIGINT UNSIGNED NOT NULL,
    requested_amount DECIMAL(12,2) NOT NULL,
    approved_amount DECIMAL(12,2) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    reason VARCHAR(500) NOT NULL,
    reject_reason VARCHAR(500) NULL,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    risk_message VARCHAR(500) NULL,
    refund_channel VARCHAR(32) NULL,
    reviewed_by BIGINT UNSIGNED NULL,
    reviewed_at DATETIME(3) NULL,
    executed_at DATETIME(3) NULL,
    expected_arrival_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_requests_no (refund_no),
    KEY idx_refund_requests_status (status, created_at),
    KEY idx_refund_requests_order (order_id),
    CONSTRAINT fk_refund_requests_order FOREIGN KEY (order_id) REFERENCES biz_orders (id),
    CONSTRAINT fk_refund_requests_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_refund_requests_requester FOREIGN KEY (requested_by) REFERENCES support_users (id),
    CONSTRAINT fk_refund_requests_reviewer FOREIGN KEY (reviewed_by) REFERENCES support_users (id),
    CONSTRAINT ck_refund_requests_amount CHECK
      (requested_amount > 0 AND (approved_amount IS NULL OR approved_amount >= 0)),
    CONSTRAINT ck_refund_requests_status CHECK
      (status IN ('SUBMITTED','UNDER_REVIEW','NEED_MORE_INFO','APPROVED','REJECTED',
                  'EXECUTING','SUCCEEDED','FAILED','CANCELLED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS portal_notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT UNSIGNED NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    related_type VARCHAR(32) NULL,
    related_no VARCHAR(64) NULL,
    read_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_portal_notifications_recipient (recipient_user_id, read_flag, created_at),
    CONSTRAINT fk_portal_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES support_users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS portal_audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_no VARCHAR(64) NOT NULL,
    result VARCHAR(20) NOT NULL,
    detail VARCHAR(1000) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_portal_audit_actor (actor_user_id, created_at),
    KEY idx_portal_audit_resource (resource_type, resource_no, created_at),
    CONSTRAINT fk_portal_audit_actor FOREIGN KEY (actor_user_id) REFERENCES support_users (id)
) ENGINE=InnoDB;
