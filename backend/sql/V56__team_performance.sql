-- Wave 15: 팀 성과 대시보드
CREATE TABLE team_member_performances (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    name                VARCHAR(100) NOT NULL,
    email               VARCHAR(200) NOT NULL,
    role                VARCHAR(100) NOT NULL,
    avatar              TEXT,
    tasks_completed     INT NOT NULL DEFAULT 0,
    tasks_assigned      INT NOT NULL DEFAULT 0,
    completion_rate     NUMERIC(5,2) NOT NULL DEFAULT 0,
    avg_completion_time NUMERIC(5,2) NOT NULL DEFAULT 0,
    content_produced    INT NOT NULL DEFAULT 0,
    on_time_rate        NUMERIC(5,2) NOT NULL DEFAULT 0,
    rating              NUMERIC(3,2) NOT NULL DEFAULT 0,
    streak              INT NOT NULL DEFAULT 0,
    last_active_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE team_activities (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    member_id       BIGINT NOT NULL REFERENCES team_member_performances(id) ON DELETE CASCADE,
    member_name     VARCHAR(100) NOT NULL,
    action          VARCHAR(200) NOT NULL,
    target          VARCHAR(300) NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_team_member_perf_workspace ON team_member_performances(workspace_id);
CREATE INDEX idx_team_activities_workspace ON team_activities(workspace_id);
CREATE INDEX idx_team_activities_member ON team_activities(member_id);
CREATE INDEX idx_team_activities_timestamp ON team_activities(workspace_id, timestamp DESC);
