-- 콘텐츠 리라이터
CREATE TABLE IF NOT EXISTS content_rewrite_results (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    source_text TEXT NOT NULL,
    source_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    target_formats VARCHAR(500) DEFAULT '[]',
    results JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_content_rewrite_results_user_id ON content_rewrite_results(user_id);
CREATE INDEX IF NOT EXISTS idx_content_rewrite_results_created_at ON content_rewrite_results(created_at);
