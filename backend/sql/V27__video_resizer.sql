-- 영상 리사이저
CREATE TABLE IF NOT EXISTS resize_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL,
    video_title VARCHAR(500),
    original_aspect_ratio VARCHAR(10),
    target_aspect_ratio VARCHAR(10) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    output_url TEXT,
    thumbnail_url TEXT,
    smart_crop BOOLEAN DEFAULT true,
    focus_point_x NUMERIC(5,2),
    focus_point_y NUMERIC(5,2),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_resize_jobs_user ON resize_jobs(user_id);
CREATE INDEX idx_resize_jobs_video ON resize_jobs(video_id);
