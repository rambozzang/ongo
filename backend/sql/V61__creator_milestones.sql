-- Wave 16: 크리에이터 마일스톤
CREATE TABLE creator_milestones (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    type            VARCHAR(20) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    target_value    BIGINT NOT NULL,
    current_value   BIGINT NOT NULL DEFAULT 0,
    progress        NUMERIC(5,2) NOT NULL DEFAULT 0,
    platform        VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    achieved_at     TIMESTAMPTZ,
    target_date     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE milestone_timelines (
    id              BIGSERIAL PRIMARY KEY,
    milestone_id    BIGINT NOT NULL REFERENCES creator_milestones(id) ON DELETE CASCADE,
    date            DATE NOT NULL,
    value           BIGINT NOT NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE milestone_rewards (
    id              BIGSERIAL PRIMARY KEY,
    milestone_id    BIGINT NOT NULL REFERENCES creator_milestones(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    is_redeemed     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_creator_milestones_workspace ON creator_milestones(workspace_id);
CREATE INDEX idx_creator_milestones_status ON creator_milestones(workspace_id, status);
CREATE INDEX idx_milestone_timelines_milestone ON milestone_timelines(milestone_id);
CREATE INDEX idx_milestone_rewards_milestone ON milestone_rewards(milestone_id);
