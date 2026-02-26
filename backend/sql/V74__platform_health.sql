-- 플랫폼 건강 점수
CREATE TABLE IF NOT EXISTS platform_health_scores (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    overall_score       INT DEFAULT 0,
    growth_score        INT DEFAULT 0,
    engagement_score    INT DEFAULT 0,
    consistency_score   INT DEFAULT 0,
    audience_score      INT DEFAULT 0,
    trend               VARCHAR(30) DEFAULT 'STABLE',
    checked_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_platform_health_workspace ON platform_health_scores(workspace_id);
CREATE INDEX idx_platform_health_platform ON platform_health_scores(platform);

-- 건강 이슈
CREATE TABLE IF NOT EXISTS health_issues (
    id                  BIGSERIAL PRIMARY KEY,
    health_score_id     BIGINT NOT NULL REFERENCES platform_health_scores(id) ON DELETE CASCADE,
    severity            VARCHAR(30) DEFAULT 'LOW',
    category            VARCHAR(100) NOT NULL,
    description         TEXT NOT NULL,
    recommendation      TEXT,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_health_issues_score ON health_issues(health_score_id);
