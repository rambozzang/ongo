-- 크로스플랫폼 분석
CREATE TABLE IF NOT EXISTS cross_platform_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    period VARCHAR(20) NOT NULL,
    contents JSONB DEFAULT '[]',
    platform_summaries JSONB DEFAULT '[]',
    audience_overlap JSONB DEFAULT '[]',
    recommendations JSONB DEFAULT '[]',
    generated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_cross_platform_reports_user ON cross_platform_reports(user_id);
CREATE INDEX idx_cross_platform_reports_period ON cross_platform_reports(period);
