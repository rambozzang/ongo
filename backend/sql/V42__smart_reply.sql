CREATE TABLE IF NOT EXISTS smart_reply_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    context VARCHAR(30) NOT NULL DEFAULT 'COMMENT',
    trigger_keywords VARCHAR(500) DEFAULT '[]',
    sentiment VARCHAR(20),
    tone VARCHAR(30) NOT NULL DEFAULT 'FRIENDLY',
    template_text TEXT,
    use_ai BOOLEAN NOT NULL DEFAULT FALSE,
    auto_send BOOLEAN NOT NULL DEFAULT FALSE,
    platform VARCHAR(30),
    reply_count INT NOT NULL DEFAULT 0,
    last_used TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smart_reply_rules_user_id ON smart_reply_rules(user_id);
CREATE INDEX IF NOT EXISTS idx_smart_reply_rules_is_active ON smart_reply_rules(is_active);

CREATE TABLE IF NOT EXISTS smart_reply_suggestions (
    id VARCHAR(100) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id BIGINT NOT NULL REFERENCES users(id),
    original_text TEXT NOT NULL,
    original_author VARCHAR(200),
    platform VARCHAR(30) NOT NULL,
    context VARCHAR(30) NOT NULL DEFAULT 'COMMENT',
    sentiment VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL',
    suggestions JSONB DEFAULT '[]',
    video_id VARCHAR(100),
    video_title VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smart_reply_suggestions_user_id ON smart_reply_suggestions(user_id);
CREATE INDEX IF NOT EXISTS idx_smart_reply_suggestions_platform ON smart_reply_suggestions(platform);

CREATE TABLE IF NOT EXISTS smart_reply_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    default_tone VARCHAR(30) NOT NULL DEFAULT 'FRIENDLY',
    enable_auto_reply BOOLEAN NOT NULL DEFAULT FALSE,
    max_auto_replies_per_day INT NOT NULL DEFAULT 50,
    exclude_keywords VARCHAR(500) DEFAULT '[]',
    reply_delay INT NOT NULL DEFAULT 0,
    platforms VARCHAR(500) DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smart_reply_configs_user_id ON smart_reply_configs(user_id);
