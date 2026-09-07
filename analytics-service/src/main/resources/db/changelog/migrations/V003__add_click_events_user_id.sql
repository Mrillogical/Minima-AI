-- liquibase formatted sql

-- changeset minima-AI:7
ALTER TABLE click_events ADD COLUMN user_id UUID;
CREATE INDEX idx_click_events_user_id ON click_events(user_id);

