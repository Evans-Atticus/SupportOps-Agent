SET NAMES utf8mb4;
USE supportops;

ALTER TABLE support_conversations
    ADD COLUMN agent_archived_at DATETIME(3) NULL AFTER risk_level;
