-- AI 브랜드 보이스
CREATE TABLE IF NOT EXISTS brand_voice_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    tone VARCHAR(30) NOT NULL DEFAULT 'CASUAL',
    train_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    sample_count INT DEFAULT 0,
    vocabulary JSONB DEFAULT '[]',
    avoid_words JSONB DEFAULT '[]',
    emoji_usage VARCHAR(20) DEFAULT 'MODERATE',
    avg_sentence_length INT DEFAULT 0,
    signature_phrase VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_brand_voice_profiles_user ON brand_voice_profiles(user_id);
