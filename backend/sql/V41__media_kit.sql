CREATE TABLE IF NOT EXISTS media_kits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    template_style VARCHAR(30) NOT NULL DEFAULT 'MODERN',
    bio TEXT,
    profile_image_url VARCHAR(500),
    platforms JSONB DEFAULT '[]',
    demographics JSONB DEFAULT '[]',
    top_content JSONB DEFAULT '[]',
    campaign_results JSONB DEFAULT '[]',
    rate_cards JSONB DEFAULT '[]',
    contact_email VARCHAR(300),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_media_kits_user_id ON media_kits(user_id);
CREATE INDEX IF NOT EXISTS idx_media_kits_status ON media_kits(status);
