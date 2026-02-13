-- Comments
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT REFERENCES videos(id),
    platform VARCHAR(20),
    platform_comment_id VARCHAR(255),
    author_name VARCHAR(100) NOT NULL,
    author_avatar_url VARCHAR(500),
    content TEXT NOT NULL,
    sentiment VARCHAR(20) DEFAULT 'NEUTRAL',
    is_replied BOOLEAN DEFAULT FALSE,
    reply_content TEXT,
    replied_at TIMESTAMP,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_comments_user_id ON comments(user_id);
CREATE INDEX idx_comments_video_id ON comments(video_id);

-- Templates
CREATE TABLE templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    title_template VARCHAR(200),
    description_template TEXT,
    tags TEXT[] DEFAULT '{}',
    category VARCHAR(50),
    platform VARCHAR(20),
    usage_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_templates_user_id ON templates(user_id);

-- Assets
CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255),
    file_url VARCHAR(500) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    tags TEXT[] DEFAULT '{}',
    folder VARCHAR(100) DEFAULT 'default',
    width INT,
    height INT,
    duration_seconds INT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_assets_user_id ON assets(user_id);

-- Watermarks
CREATE TABLE watermarks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    position VARCHAR(20) DEFAULT 'BOTTOM_RIGHT',
    opacity DECIMAL(3,2) DEFAULT 0.8,
    size INT DEFAULT 100,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_watermarks_user_id ON watermarks(user_id);
