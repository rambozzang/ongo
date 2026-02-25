-- 해시태그 전략
CREATE TABLE IF NOT EXISTS hashtag_sets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    hashtags JSONB DEFAULT '[]',
    total_reach_estimate BIGINT DEFAULT 0,
    overall_difficulty VARCHAR(20),
    score INT DEFAULT 0,
    platform VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_hashtag_sets_user ON hashtag_sets(user_id);
