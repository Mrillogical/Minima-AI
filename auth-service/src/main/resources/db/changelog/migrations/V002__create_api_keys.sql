-- liquibase formatted sql

-- changeset minima-AI:6
-- api_keys â€” MCP authentication credentials, one user can have many keys
CREATE TABLE api_keys (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- key dies with user
    key_prefix VARCHAR(8)   NOT NULL,          -- first 8 chars of random part, shown in list UI
    key_hash   VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256 hex of full key â€” never store raw
    name       VARCHAR(100) NOT NULL,          -- user label: "my cursor plugin", "n8n workflow"
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- changeset minima-AI:7
CREATE INDEX idx_api_keys_user ON api_keys(user_id);
-- key_hash already has UNIQUE constraint which creates an index

