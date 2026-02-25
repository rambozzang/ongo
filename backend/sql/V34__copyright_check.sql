-- 저작권 사전검증
CREATE TABLE IF NOT EXISTS copyright_check_results (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL,
    video_title VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    issues JSONB DEFAULT '[]',
    music_detected JSONB DEFAULT '[]',
    monetization_eligible BOOLEAN DEFAULT TRUE,
    platform_checks JSONB DEFAULT '[]',
    checked_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_copyright_check_results_user ON copyright_check_results(user_id);
CREATE INDEX idx_copyright_check_results_video ON copyright_check_results(video_id);
CREATE INDEX idx_copyright_check_results_status ON copyright_check_results(status);
