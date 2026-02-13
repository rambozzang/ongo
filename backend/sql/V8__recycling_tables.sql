-- ============================================================================
-- onGo - Creator Multi-Platform Management SaaS
-- Flyway Migration: V8__recycling_tables.sql
-- Content recycling suggestion tables
-- ============================================================================

-- Recycling Suggestions
CREATE TABLE recycling_suggestions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL REFERENCES videos(id),
    suggestion_type VARCHAR(30) NOT NULL,
    reason TEXT,
    suggested_platforms TEXT[] DEFAULT '{}',
    priority_score INT DEFAULT 50,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_recycling_suggestions_user_id ON recycling_suggestions(user_id);
CREATE INDEX idx_recycling_suggestions_status ON recycling_suggestions(user_id, status);
