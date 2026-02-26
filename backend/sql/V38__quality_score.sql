-- 품질 점수
CREATE TABLE IF NOT EXISTS quality_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT,
    video_title VARCHAR(500),
    overall_score INT NOT NULL DEFAULT 0,
    overall_grade VARCHAR(10) NOT NULL DEFAULT 'F',
    metrics JSONB DEFAULT '{}',
    improvements JSONB DEFAULT '[]',
    competitor_avg INT DEFAULT 0,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quality_reports_user_id ON quality_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_quality_reports_video_id ON quality_reports(video_id);
CREATE INDEX IF NOT EXISTS idx_quality_reports_overall_score ON quality_reports(overall_score);
CREATE INDEX IF NOT EXISTS idx_quality_reports_checked_at ON quality_reports(checked_at);
