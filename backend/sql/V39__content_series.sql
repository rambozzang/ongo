CREATE TABLE IF NOT EXISTS content_series (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    description TEXT,
    cover_image_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    platform VARCHAR(30) NOT NULL DEFAULT 'YOUTUBE',
    frequency VARCHAR(30) NOT NULL DEFAULT 'WEEKLY',
    custom_frequency_days INT,
    tags VARCHAR(500) DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_content_series_user_id ON content_series(user_id);
CREATE INDEX IF NOT EXISTS idx_content_series_status ON content_series(status);

CREATE TABLE IF NOT EXISTS series_episodes (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES content_series(id) ON DELETE CASCADE,
    episode_number INT NOT NULL DEFAULT 1,
    title VARCHAR(300) NOT NULL,
    video_id VARCHAR(100),
    platform VARCHAR(30) NOT NULL DEFAULT 'YOUTUBE',
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    scheduled_date TIMESTAMPTZ,
    published_date TIMESTAMPTZ,
    views BIGINT DEFAULT 0,
    likes BIGINT DEFAULT 0,
    comments BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_series_episodes_series_id ON series_episodes(series_id);
CREATE INDEX IF NOT EXISTS idx_series_episodes_status ON series_episodes(status);
