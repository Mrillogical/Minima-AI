-- liquibase formatted sql

-- changeset minima-AI:1
-- Core URL table â€” every shortened URL lives here
CREATE TABLE urls (
    id            BIGSERIAL     PRIMARY KEY,                 -- auto-increment; Base62-encoded to form the slug
    slug          VARCHAR(20)   NOT NULL UNIQUE,             -- Base62 short code e.g. "aB3xK2p"
    original_url  TEXT          NOT NULL,                    -- full destination URL
    user_id       BIGINT        NOT NULL,                    -- owner (no FK â€” cross-service boundary)
    title         VARCHAR(255),                              -- AI-generated or user-provided
    category      VARCHAR(100),                              -- AI classification result
    is_safe       BOOLEAN       NOT NULL DEFAULT TRUE,       -- AI safety check result
    is_custom     BOOLEAN       NOT NULL DEFAULT FALSE,      -- true if user provided the slug
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,       -- soft delete â€” never hard-delete URLs
    click_count   BIGINT        NOT NULL DEFAULT 0,          -- denormalized for fast reads
    expires_at    TIMESTAMPTZ,                               -- null = never expires
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- changeset minima-AI:2
-- Indexes â€” redirect lookup is the hottest query in the entire system
CREATE INDEX idx_urls_slug       ON urls(slug);
CREATE INDEX idx_urls_user_id    ON urls(user_id);
CREATE INDEX idx_urls_expires_at ON urls(expires_at)
    WHERE expires_at IS NOT NULL;                            -- partial index â€” skips non-expiring rows

-- changeset minima-AI:3
-- ShedLock table â€” prevents duplicate scheduled job execution across multiple instances
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- changeset minima-AI:4
ALTER TABLE urls DROP COLUMN user_id;
ALTER TABLE urls ADD COLUMN user_id UUID NOT NULL;



