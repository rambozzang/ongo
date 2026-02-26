CREATE TABLE IF NOT EXISTS funding_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    source VARCHAR(50) NOT NULL DEFAULT 'SUPER_CHAT',
    platform VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    donor_name VARCHAR(200),
    message TEXT,
    video_id VARCHAR(100),
    video_title VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_funding_transactions_user_id ON funding_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_funding_transactions_source ON funding_transactions(source);
CREATE INDEX IF NOT EXISTS idx_funding_transactions_platform ON funding_transactions(platform);
CREATE INDEX IF NOT EXISTS idx_funding_transactions_created_at ON funding_transactions(created_at);

CREATE TABLE IF NOT EXISTS funding_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    target_amount BIGINT NOT NULL DEFAULT 0,
    current_amount BIGINT NOT NULL DEFAULT 0,
    deadline TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_funding_goals_user_id ON funding_goals(user_id);
CREATE INDEX IF NOT EXISTS idx_funding_goals_is_active ON funding_goals(is_active);
