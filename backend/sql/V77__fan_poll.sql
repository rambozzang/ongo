-- 팬 투표
CREATE TABLE fan_polls (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    type            VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_votes     INT NOT NULL DEFAULT 0,
    start_date      DATE,
    end_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE poll_options (
    id              BIGSERIAL PRIMARY KEY,
    poll_id         BIGINT NOT NULL REFERENCES fan_polls(id) ON DELETE CASCADE,
    text            VARCHAR(500) NOT NULL,
    votes           INT NOT NULL DEFAULT 0,
    percentage      NUMERIC(5,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_fan_polls_user ON fan_polls(user_id);
CREATE INDEX idx_fan_polls_status ON fan_polls(status);
CREATE INDEX idx_poll_options_poll ON poll_options(poll_id);
