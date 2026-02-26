-- 크리에이터 네트워크
CREATE TABLE creator_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    avatar_url      VARCHAR(1000),
    platform        VARCHAR(50) NOT NULL,
    subscribers     BIGINT NOT NULL DEFAULT 0,
    category        VARCHAR(100),
    match_score     INT NOT NULL DEFAULT 0,
    is_connected    BOOLEAN NOT NULL DEFAULT false,
    bio             TEXT,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE collaboration_requests (
    id                  BIGSERIAL PRIMARY KEY,
    from_creator_id     BIGINT NOT NULL REFERENCES creator_profiles(id),
    to_creator_id       BIGINT NOT NULL REFERENCES creator_profiles(id),
    message             TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    proposed_type       VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_creator_profiles_user ON creator_profiles(user_id);
CREATE INDEX idx_creator_profiles_category ON creator_profiles(category);
CREATE INDEX idx_collab_requests_from ON collaboration_requests(from_creator_id);
CREATE INDEX idx_collab_requests_to ON collaboration_requests(to_creator_id);
CREATE INDEX idx_collab_requests_status ON collaboration_requests(status);
