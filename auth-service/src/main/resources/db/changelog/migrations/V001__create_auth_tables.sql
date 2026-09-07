-- liquibase formatted sql
-- changeset minima-AI:1
-- Users table â€” core identity, owns everything else
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- UUID safer than serial (no enumeration)
    email       VARCHAR(255) NOT NULL UNIQUE,                -- login identifier
    password    VARCHAR(255),                                -- nullable â€” OAuth2 users have no password
    name        VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'ROLE_FREE',   -- ROLE_FREE, ROLE_PRO, ROLE_ADMIN
    provider    VARCHAR(50)  NOT NULL DEFAULT 'LOCAL',       -- LOCAL or GOOGLE
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,         -- email verification gate
     version     BIGINT       NOT NULL DEFAULT 0,             -- optimistic locking â€” JPA checks this on every update
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- changeset minima-AI:2
-- Refresh tokens â€” stored in DB as backup, Redis is primary lookup
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,               -- hash only, never store raw token
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- changeset minima-AI:3
-- Audit log â€” security events: login, logout, password change, OAuth2
CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    event_type  VARCHAR(100) NOT NULL,                      -- LOGIN_SUCCESS, LOGIN_FAILED, etc.
    ip_address  VARCHAR(45),                                -- IPv6 max length = 45
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- changeset minima-AI:4
-- Indexes â€” speed up common queries
CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_refresh_tokens_user  ON refresh_tokens(user_id);
CREATE INDEX idx_audit_log_user       ON audit_log(user_id);
CREATE INDEX idx_audit_log_created    ON audit_log(created_at);

-- changeset minima-AI:5
ALTER TABLE audit_log DROP CONSTRAINT audit_log_user_id_fkey;

