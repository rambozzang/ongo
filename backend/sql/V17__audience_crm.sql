-- V17__audience_crm.sql
-- 팬/오디언스 CRM

CREATE TABLE audience_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    author_name         VARCHAR(200) NOT NULL,
    platform            VARCHAR(50),
    avatar_url          TEXT,
    engagement_score    DOUBLE PRECISION NOT NULL DEFAULT 0,
    tags                JSONB DEFAULT '[]',
    total_interactions  INT NOT NULL DEFAULT 0,
    positive_ratio      DOUBLE PRECISION DEFAULT 0,
    first_seen_at       TIMESTAMP,
    last_seen_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audience_profiles_user ON audience_profiles(user_id);
CREATE INDEX idx_audience_profiles_score ON audience_profiles(engagement_score DESC);
CREATE UNIQUE INDEX idx_audience_profiles_unique ON audience_profiles(user_id, author_name, platform);

CREATE TABLE audience_segments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    conditions  JSONB NOT NULL DEFAULT '{}',
    auto_update BOOLEAN NOT NULL DEFAULT TRUE,
    member_count INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audience_segments_user ON audience_segments(user_id);

CREATE TABLE audience_profile_segments (
    profile_id  BIGINT NOT NULL REFERENCES audience_profiles(id) ON DELETE CASCADE,
    segment_id  BIGINT NOT NULL REFERENCES audience_segments(id) ON DELETE CASCADE,
    PRIMARY KEY (profile_id, segment_id)
);
