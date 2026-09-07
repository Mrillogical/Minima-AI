-- liquibase formatted sql

-- changeset minima-AI:5
-- Dead letter queue table - stores Kafka events that failed to publish
-- DlqRetryJob reads this every 15 min and republishes unprocessed rows
CREATE TABLE failed_events (
    id               BIGSERIAL     PRIMARY KEY,
    topic            VARCHAR(100)  NOT NULL,          -- Kafka topic e.g. "url.created"
    event_key        VARCHAR(100)  NOT NULL,           -- Kafka message key (slug)
    payload          TEXT          NOT NULL,           -- JSON-serialized event
    error_message    TEXT,                             -- what went wrong
    retry_count      INT           NOT NULL DEFAULT 0, -- how many retry attempts so far
    processed        BOOLEAN       NOT NULL DEFAULT FALSE, -- true = successfully retried
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_attempt_at  TIMESTAMPTZ                        -- null until first retry
);

-- changeset minima-AI:6
CREATE INDEX idx_failed_events_processed ON failed_events(processed)
    WHERE processed = FALSE;   -- partial index â€” only unprocessed rows, keeps it tiny

