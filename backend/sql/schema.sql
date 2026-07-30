-- SupportOps Agent / MySQL 8.0+
-- All timestamps are stored with millisecond precision. The application and
-- connection must use Asia/Shanghai consistently for the demo environment.

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS supportops
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE supportops;

CREATE TABLE support_users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role_code VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    daily_quota INT UNSIGNED NOT NULL DEFAULT 50,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_users_username (username),
    CONSTRAINT ck_support_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE customers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_no VARCHAR(40) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    mobile_masked VARCHAR(32) NULL,
    email_masked VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_customer_no (customer_no)
) ENGINE=InnoDB;

CREATE TABLE tickets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_no VARCHAR(40) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    business_no VARCHAR(64) NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WEB',
    description VARCHAR(2000) NOT NULL,
    scenario_hint VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_ticket_no (ticket_no),
    KEY idx_tickets_customer_created (customer_id, created_at),
    KEY idx_tickets_business_no (business_no),
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_tickets_status CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
) ENGINE=InnoDB;

CREATE TABLE biz_orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_status VARCHAR(24) NOT NULL,
    payment_status VARCHAR(24) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    payable_amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    coupon_code VARCHAR(40) NULL,
    product_scope VARCHAR(100) NULL,
    paid_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_orders_order_no (order_no),
    KEY idx_biz_orders_customer_created (customer_id, created_at),
    CONSTRAINT fk_biz_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_biz_orders_amount CHECK (total_amount >= 0 AND payable_amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE payment_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(24) NOT NULL,
    payment_status VARCHAR(24) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    callback_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    callback_error_code VARCHAR(64) NULL,
    callback_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    paid_at DATETIME(3) NULL,
    callback_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_records_payment_no (payment_no),
    KEY idx_payment_records_order (order_id),
    CONSTRAINT fk_payment_records_order FOREIGN KEY (order_id) REFERENCES biz_orders (id)
) ENGINE=InnoDB;

CREATE TABLE refund_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    payment_id BIGINT UNSIGNED NULL,
    refund_status VARCHAR(24) NOT NULL,
    refund_amount DECIMAL(12,2) NOT NULL,
    failure_code VARCHAR(64) NULL,
    requested_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_records_refund_no (refund_no),
    KEY idx_refund_records_order (order_id),
    CONSTRAINT fk_refund_records_order FOREIGN KEY (order_id) REFERENCES biz_orders (id),
    CONSTRAINT fk_refund_records_payment FOREIGN KEY (payment_id) REFERENCES payment_records (id)
) ENGINE=InnoDB;

CREATE TABLE coupons (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    coupon_code VARCHAR(40) NOT NULL,
    coupon_name VARCHAR(100) NOT NULL,
    coupon_status VARCHAR(20) NOT NULL,
    threshold_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    product_scope VARCHAR(100) NOT NULL DEFAULT 'ALL',
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupons_coupon_code (coupon_code),
    CONSTRAINT ck_coupons_validity CHECK (valid_until > valid_from)
) ENGINE=InnoDB;

CREATE TABLE customer_coupons (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    coupon_id BIGINT UNSIGNED NOT NULL,
    receive_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    used_order_id BIGINT UNSIGNED NULL,
    received_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_coupons_owner (customer_id, coupon_id),
    CONSTRAINT fk_customer_coupons_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_coupons_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_customer_coupons_order FOREIGN KEY (used_order_id) REFERENCES biz_orders (id)
) ENGINE=InnoDB;

CREATE TABLE member_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_no VARCHAR(40) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    member_level VARCHAR(24) NOT NULL,
    member_status VARCHAR(20) NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_accounts_member_no (member_no),
    UNIQUE KEY uk_member_accounts_customer (customer_id),
    CONSTRAINT fk_member_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB;

CREATE TABLE member_benefit_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    benefit_no VARCHAR(64) NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    benefit_code VARCHAR(40) NOT NULL,
    benefit_name VARCHAR(100) NOT NULL,
    grant_status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    expected_at DATETIME(3) NULL,
    granted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_benefit_records_no (benefit_no),
    KEY idx_member_benefit_records_member (member_id, benefit_code),
    CONSTRAINT fk_member_benefit_records_member FOREIGN KEY (member_id) REFERENCES member_accounts (id)
) ENGINE=InnoDB;

CREATE TABLE logistics_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tracking_no VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    carrier_name VARCHAR(80) NULL,
    logistics_status VARCHAR(32) NOT NULL,
    status_description VARCHAR(255) NULL,
    origin_location VARCHAR(160) NULL,
    destination_location VARCHAR(160) NULL,
    current_location VARCHAR(160) NULL,
    facility_name VARCHAR(120) NULL,
    courier_name_masked VARCHAR(60) NULL,
    courier_phone_masked VARCHAR(32) NULL,
    estimated_delivery_at DATETIME(3) NULL,
    event_time DATETIME(3) NOT NULL,
    synced_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_logistics_tracking_source (tracking_no, source_type, event_time),
    KEY idx_logistics_order (order_id),
    CONSTRAINT fk_logistics_records_order FOREIGN KEY (order_id) REFERENCES biz_orders (id),
    CONSTRAINT ck_logistics_source CHECK (source_type IN ('LOCAL', 'CARRIER'))
) ENGINE=InnoDB;

CREATE TABLE api_call_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    client_code VARCHAR(64) NOT NULL,
    api_name VARCHAR(100) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    request_status VARCHAR(20) NOT NULL,
    http_status SMALLINT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    duration_ms INT UNSIGNED NOT NULL,
    called_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_call_records_trace (trace_id),
    KEY idx_api_call_records_window (client_code, api_name, called_at),
    KEY idx_api_call_records_error (error_code, called_at)
) ENGINE=InnoDB;

CREATE TABLE invoice_applications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    invoice_no VARCHAR(64) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    invoice_type VARCHAR(24) NOT NULL,
    title VARCHAR(200) NOT NULL,
    tax_no VARCHAR(64) NULL,
    email_masked VARCHAR(128) NULL,
    qualification_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    issue_status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    issued_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_applications_no (invoice_no),
    KEY idx_invoice_applications_order (order_id),
    CONSTRAINT fk_invoice_applications_order FOREIGN KEY (order_id) REFERENCES biz_orders (id)
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

CREATE TABLE sop_definitions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scenario_type VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    audience VARCHAR(160) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 1,
    content_json JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sop_scenario_version (scenario_type, version)
) ENGINE=InnoDB;

CREATE TABLE diagnosis_tasks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    requested_by BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    scenario_type VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    confidence DECIMAL(5,4) NULL,
    degraded TINYINT(1) NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    model_call_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnosis_tasks_idempotency (requested_by, idempotency_key),
    KEY idx_diagnosis_tasks_ticket_created (ticket_id, created_at),
    KEY idx_diagnosis_tasks_status_created (status, created_at),
    CONSTRAINT fk_diagnosis_tasks_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_diagnosis_tasks_user FOREIGN KEY (requested_by) REFERENCES support_users (id),
    CONSTRAINT ck_diagnosis_tasks_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_diagnosis_tasks_model_calls CHECK (model_call_count <= 3)
) ENGINE=InnoDB;

CREATE TABLE diagnosis_steps (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    diagnosis_id BIGINT UNSIGNED NOT NULL,
    step_order SMALLINT UNSIGNED NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    duration_ms INT UNSIGNED NULL,
    detail_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnosis_steps_order (diagnosis_id, step_order),
    CONSTRAINT fk_diagnosis_steps_task FOREIGN KEY (diagnosis_id) REFERENCES diagnosis_tasks (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE diagnosis_reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    diagnosis_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    conclusion VARCHAR(1000) NOT NULL,
    internal_suggestion VARCHAR(1000) NOT NULL,
    customer_reply VARCHAR(2000) NULL,
    sop_id BIGINT UNSIGNED NULL,
    adopted_at DATETIME(3) NULL,
    discarded_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnosis_reports_task (diagnosis_id),
    CONSTRAINT fk_diagnosis_reports_task FOREIGN KEY (diagnosis_id) REFERENCES diagnosis_tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_diagnosis_reports_sop FOREIGN KEY (sop_id) REFERENCES sop_definitions (id)
) ENGINE=InnoDB;

CREATE TABLE diagnosis_evidences (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    diagnosis_id BIGINT UNSIGNED NOT NULL,
    evidence_order SMALLINT UNSIGNED NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    source_record_id BIGINT UNSIGNED NULL,
    source_field VARCHAR(64) NOT NULL,
    label VARCHAR(100) NOT NULL,
    evidence_value VARCHAR(500) NOT NULL,
    description VARCHAR(500) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_diagnosis_evidences_order (diagnosis_id, evidence_order),
    CONSTRAINT fk_diagnosis_evidences_task FOREIGN KEY (diagnosis_id) REFERENCES diagnosis_tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_diagnosis_evidences_confidence CHECK (confidence BETWEEN 0 AND 1)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS diagnosis_attachments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    diagnosis_id BIGINT UNSIGNED NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    file_content LONGBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_diagnosis_attachments_task (diagnosis_id, created_at),
    CONSTRAINT fk_diagnosis_attachments_task FOREIGN KEY (diagnosis_id)
      REFERENCES diagnosis_tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_diagnosis_attachments_size CHECK (size_bytes <= 5242880)
) ENGINE=InnoDB;

CREATE TABLE model_call_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    diagnosis_id BIGINT UNSIGNED NULL,
    request_id VARCHAR(128) NOT NULL,
    call_type VARCHAR(32) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    call_status VARCHAR(24) NOT NULL,
    input_tokens INT UNSIGNED NULL,
    output_tokens INT UNSIGNED NULL,
    duration_ms INT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_model_call_logs_diagnosis (diagnosis_id, created_at),
    KEY idx_model_call_logs_request (request_id),
    CONSTRAINT fk_model_call_logs_task FOREIGN KEY (diagnosis_id) REFERENCES diagnosis_tasks (id) ON DELETE SET NULL
) ENGINE=InnoDB;
