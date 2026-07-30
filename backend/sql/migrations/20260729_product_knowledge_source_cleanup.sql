SET NAMES utf8mb4;
USE supportops;

-- SOP 是一种文档类型，不是独立的数据来源。ERP 内的 SOP/PIM 模块统一以 ERP 来源同步。
ALTER TABLE product_knowledge_documents
    DROP CHECK ck_product_knowledge_source;

ALTER TABLE product_knowledge_documents
    ADD CONSTRAINT ck_product_knowledge_source CHECK (source_type IN ('ERP','MANUAL'));
