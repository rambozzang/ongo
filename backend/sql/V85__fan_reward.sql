-- 팬 리워드
CREATE TABLE fan_rewards (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    points_cost     INT NOT NULL DEFAULT 0,
    category        VARCHAR(50) NOT NULL DEFAULT 'BADGE',
    is_active       BOOLEAN NOT NULL DEFAULT true,
    claimed_count   INT NOT NULL DEFAULT 0,
    max_claims      INT,
    image_url       VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fan_activities (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    fan_name        VARCHAR(200) NOT NULL,
    activity_type   VARCHAR(50) NOT NULL,
    points          INT NOT NULL DEFAULT 0,
    description     TEXT,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fan_rewards_user ON fan_rewards(user_id);
CREATE INDEX idx_fan_rewards_category ON fan_rewards(category);
CREATE INDEX idx_fan_rewards_active ON fan_rewards(is_active);
CREATE INDEX idx_fan_activities_user ON fan_activities(user_id);
CREATE INDEX idx_fan_activities_type ON fan_activities(activity_type);
