-- Competitors
CREATE TABLE competitors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform VARCHAR(20) NOT NULL,
    platform_channel_id VARCHAR(255) NOT NULL,
    channel_name VARCHAR(200) NOT NULL,
    channel_url VARCHAR(500),
    subscriber_count BIGINT DEFAULT 0,
    total_views BIGINT DEFAULT 0,
    video_count INT DEFAULT 0,
    avg_views BIGINT DEFAULT 0,
    profile_image_url VARCHAR(500),
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_competitors_user_id ON competitors(user_id);

-- A/B Tests
CREATE TABLE ab_tests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT REFERENCES videos(id),
    test_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    metric_type VARCHAR(30) DEFAULT 'CTR',
    winner_variant_id BIGINT,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_ab_tests_user_id ON ab_tests(user_id);

-- A/B Test Variants
CREATE TABLE ab_test_variants (
    id BIGSERIAL PRIMARY KEY,
    test_id BIGINT NOT NULL REFERENCES ab_tests(id) ON DELETE CASCADE,
    variant_name VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    description TEXT,
    thumbnail_url VARCHAR(500),
    views BIGINT DEFAULT 0,
    clicks BIGINT DEFAULT 0,
    engagement_rate DECIMAL(5,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_ab_test_variants_test_id ON ab_test_variants(test_id);
