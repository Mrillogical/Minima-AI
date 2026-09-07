-- liquibase formatted sql

-- changeset minima-AI:5
-- Fix click_hourly.url_id type to match click_events.url_id (BIGINT)
-- Table is empty in dev â€” USING clause safe, no real UUID data to convert
ALTER TABLE click_hourly ALTER COLUMN url_id TYPE BIGINT USING url_id::text::bigint;

-- changeset minima-AI:6
-- Drop old UUID index, recreate for BIGINT â€” type change invalidates it
DROP INDEX IF EXISTS idx_click_hourly_url_id;
CREATE INDEX idx_click_hourly_url_id ON click_hourly(url_id);

