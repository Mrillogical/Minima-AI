-- Runs once on first container start
-- Each service owns its own database â€” no cross-service DB access

CREATE DATABASE minima-AI_auth;       -- auth-service
CREATE DATABASE minima-AI_urls;       -- url-service
CREATE DATABASE minima-AI_analytics;  -- analytics-service
CREATE DATABASE minima-AI_ai;         -- ai-service

