-- 경쟁자 분석
CREATE TABLE IF NOT EXISTS competitor_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    avatar_url TEXT,
    platforms JSONB DEFAULT '[]',
    subscriber_count BIGINT DEFAULT 0,
    avg_views BIGINT DEFAULT 0,
    avg_engagement NUMERIC(5,2) DEFAULT 0,
    added_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_competitor_profiles_user ON competitor_profiles(user_id);

CREATE TABLE IF NOT EXISTS competitor_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    period VARCHAR(10) NOT NULL,
    comparison JSONB DEFAULT '{}',
    content_gaps JSONB DEFAULT '[]',
    trending_topics JSONB DEFAULT '[]',
    generated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_competitor_reports_user ON competitor_reports(user_id);
