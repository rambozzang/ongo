-- 자막 편집기
CREATE TABLE IF NOT EXISTS subtitle_tracks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL,
    video_title VARCHAR(500),
    language VARCHAR(10) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    cues JSONB DEFAULT '[]',
    total_duration NUMERIC(10,2) DEFAULT 0,
    word_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_subtitle_tracks_user ON subtitle_tracks(user_id);
CREATE INDEX idx_subtitle_tracks_video ON subtitle_tracks(video_id);
