CREATE TABLE IF NOT EXISTS video_favorites (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, video_id)
);

CREATE INDEX IF NOT EXISTS idx_video_favorites_user_created
    ON video_favorites(user_id, created_at DESC);
