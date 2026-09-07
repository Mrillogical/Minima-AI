-- liquibase formatted sql

-- changeset minima-AI:3
-- Spring AI's JdbcChatMemoryRepository now orders messages by sequence_id,
-- not just timestamp (see V001 comment - that migration predates this).
-- Add nullable first so we can backfill existing rows before enforcing NOT NULL.
ALTER TABLE SPRING_AI_CHAT_MEMORY
    ADD COLUMN sequence_id BIGINT;

-- changeset minima-AI:4
-- Backfill: assign per-conversation sequence numbers to any rows that
-- already exist, using the same ordering the old code relied on (timestamp).
-- Safe to run even on an empty table.
UPDATE SPRING_AI_CHAT_MEMORY t
SET sequence_id = sub.rn
FROM (
    SELECT ctid, ROW_NUMBER() OVER (
        PARTITION BY conversation_id ORDER BY "timestamp"
    ) AS rn
    FROM SPRING_AI_CHAT_MEMORY
) sub
WHERE t.ctid = sub.ctid;

-- changeset minima-AI:5
ALTER TABLE SPRING_AI_CHAT_MEMORY
    ALTER COLUMN sequence_id SET NOT NULL;

-- changeset minima-AI:6
-- Composite index for the sequence_id-ordered per-conversation lookup -
-- same reasoning as the existing conversation_id+timestamp index in V001.
CREATE INDEX SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, sequence_id);

