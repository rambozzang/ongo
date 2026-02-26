-- Wave 16: 플랫폼 알고리즘 인사이트
CREATE TABLE algorithm_insights (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    platform        VARCHAR(30) NOT NULL,
    factor          VARCHAR(100) NOT NULL,
    importance      INT NOT NULL DEFAULT 0,
    current_score   INT NOT NULL DEFAULT 0,
    recommendation  TEXT,
    category        VARCHAR(20) NOT NULL,
    trend           VARCHAR(10) NOT NULL DEFAULT 'STABLE',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE algorithm_scores (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    platform            VARCHAR(30) NOT NULL,
    overall_score       INT NOT NULL DEFAULT 0,
    content_score       INT NOT NULL DEFAULT 0,
    engagement_score    INT NOT NULL DEFAULT 0,
    metadata_score      INT NOT NULL DEFAULT 0,
    consistency_score   INT NOT NULL DEFAULT 0,
    audience_score      INT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE algorithm_changes (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    platform        VARCHAR(30) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT NOT NULL,
    impact          VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    affected_areas  JSONB NOT NULL DEFAULT '[]',
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    recommendation  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_algorithm_insights_workspace ON algorithm_insights(workspace_id);
CREATE INDEX idx_algorithm_insights_platform ON algorithm_insights(workspace_id, platform);
CREATE INDEX idx_algorithm_scores_workspace ON algorithm_scores(workspace_id);
CREATE INDEX idx_algorithm_changes_workspace ON algorithm_changes(workspace_id);
