-- 소셜 리스닝
CREATE TABLE IF NOT EXISTS brand_mentions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform VARCHAR(30) NOT NULL,
    mention_text TEXT,
    author_name VARCHAR(200),
    author_url VARCHAR(500),
    sentiment VARCHAR(20) DEFAULT 'NEUTRAL',
    reach BIGINT DEFAULT 0,
    source_url VARCHAR(500),
    mentioned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_mentions_user_id ON brand_mentions(user_id);
CREATE INDEX IF NOT EXISTS idx_brand_mentions_platform ON brand_mentions(platform);
CREATE INDEX IF NOT EXISTS idx_brand_mentions_sentiment ON brand_mentions(sentiment);
CREATE INDEX IF NOT EXISTS idx_brand_mentions_mentioned_at ON brand_mentions(mentioned_at);

CREATE TABLE IF NOT EXISTS keyword_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    keyword VARCHAR(200) NOT NULL,
    platforms VARCHAR(500) DEFAULT '[]',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notify_email BOOLEAN DEFAULT FALSE,
    notify_push BOOLEAN DEFAULT TRUE,
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_keyword_alerts_user_id ON keyword_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_keyword_alerts_enabled ON keyword_alerts(enabled);
