-- 팬 인구통계
CREATE TABLE IF NOT EXISTS fan_demographics (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    age_group       VARCHAR(20) NOT NULL,
    gender          VARCHAR(20) NOT NULL,
    percentage      NUMERIC(5,2) DEFAULT 0,
    country         VARCHAR(100),
    city            VARCHAR(100),
    updated_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_fan_demographics_workspace ON fan_demographics(workspace_id);
CREATE INDEX idx_fan_demographics_platform ON fan_demographics(workspace_id, platform);

-- 팬 행동 패턴
CREATE TABLE IF NOT EXISTS fan_behaviors (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    active_hour     INT DEFAULT 0,
    active_day      VARCHAR(20),
    watch_duration  NUMERIC(5,2) DEFAULT 0,
    return_rate     NUMERIC(5,2) DEFAULT 0,
    comment_rate    NUMERIC(5,2) DEFAULT 0,
    share_rate      NUMERIC(5,2) DEFAULT 0,
    updated_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_fan_behaviors_workspace ON fan_behaviors(workspace_id);

-- 팬 세그먼트
CREATE TABLE IF NOT EXISTS fan_segments (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    member_count    INT DEFAULT 0,
    avg_engagement  NUMERIC(5,2) DEFAULT 0,
    top_interests   JSONB DEFAULT '[]',
    platform        VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_fan_segments_workspace ON fan_segments(workspace_id);
