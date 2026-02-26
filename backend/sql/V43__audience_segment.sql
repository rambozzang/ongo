CREATE TABLE IF NOT EXISTS audience_segments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    criteria JSONB DEFAULT '{}',
    size BIGINT DEFAULT 0,
    percentage NUMERIC(5,2) DEFAULT 0.0,
    avg_watch_time NUMERIC(10,2) DEFAULT 0.0,
    avg_retention NUMERIC(5,2) DEFAULT 0.0,
    avg_ctr NUMERIC(5,2) DEFAULT 0.0,
    avg_engagement NUMERIC(5,2) DEFAULT 0.0,
    growth_rate NUMERIC(5,2) DEFAULT 0.0,
    revenue_contribution NUMERIC(5,2) DEFAULT 0.0,
    top_content_categories VARCHAR(500) DEFAULT '[]',
    best_posting_times JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audience_segments_user_id ON audience_segments(user_id);
CREATE INDEX IF NOT EXISTS idx_audience_segments_type ON audience_segments(type);
