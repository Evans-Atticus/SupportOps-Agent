SET NAMES utf8mb4;
USE supportops;

ALTER TABLE support_conversations
    ADD COLUMN related_diagnosis_id BIGINT UNSIGNED NULL AFTER related_ticket_id,
    ADD KEY idx_support_conversations_diagnosis (related_diagnosis_id),
    ADD CONSTRAINT fk_support_conversations_diagnosis
        FOREIGN KEY (related_diagnosis_id) REFERENCES diagnosis_tasks (id);
