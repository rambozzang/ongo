-- Wave 14: AI 해시태그 분석기
CREATE TABLE hashtag_performances (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    hashtag         VARCHAR(100) NOT NULL,
    platform        VARCHAR(20) NOT NULL,
    usage_count     INT NOT NULL DEFAULT 0,
    total_views     BIGINT NOT NULL DEFAULT 0,
    avg_engagement  DECIMAL(5,2) NOT NULL DEFAULT 0,
    growth_rate     DECIMAL(6,2) NOT NULL DEFAULT 0,
    trend_direction VARCHAR(10) NOT NULL DEFAULT 'STABLE',
    category        VARCHAR(50),
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hashtag_groups (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    name            VARCHAR(100) NOT NULL,
    hashtags        JSONB NOT NULL DEFAULT '[]',
    platform        VARCHAR(20) NOT NULL,
    usage_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_hashtag_performances_workspace ON hashtag_performances(workspace_id);
CREATE INDEX idx_hashtag_performances_platform ON hashtag_performances(workspace_id, platform);
CREATE INDEX idx_hashtag_groups_workspace ON hashtag_groups(workspace_id);
