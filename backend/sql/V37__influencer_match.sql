-- 인플루언서 매칭
CREATE TABLE IF NOT EXISTS influencer_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    channel_name VARCHAR(300) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    subscriber_count BIGINT DEFAULT 0,
    avg_views BIGINT DEFAULT 0,
    engagement_rate NUMERIC(5,2) DEFAULT 0.0,
    categories VARCHAR(500) DEFAULT '[]',
    audience_demographics JSONB DEFAULT '{}',
    contact_email VARCHAR(300),
    profile_url VARCHAR(500),
    match_score INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_influencer_profiles_user_id ON influencer_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_influencer_profiles_platform ON influencer_profiles(platform);
CREATE INDEX IF NOT EXISTS idx_influencer_profiles_match_score ON influencer_profiles(match_score);

CREATE TABLE IF NOT EXISTS collab_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    influencer_profile_id BIGINT NOT NULL REFERENCES influencer_profiles(id),
    message TEXT,
    proposed_budget BIGINT DEFAULT 0,
    proposed_type VARCHAR(50) DEFAULT 'COLLABORATION',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    response_message TEXT,
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_collab_requests_user_id ON collab_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_collab_requests_influencer ON collab_requests(influencer_profile_id);
CREATE INDEX IF NOT EXISTS idx_collab_requests_status ON collab_requests(status);
