-- V27: 분석 전반 테이블
-- competitor, hashtag, algorithm, calendar, channel_health

-- 1. 경쟁자 분석
CREATE TABLE IF NOT EXISTS competitor_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    avatar_url TEXT,
    platforms JSONB DEFAULT '[]',
    subscriber_count BIGINT DEFAULT 0,
    avg_views BIGINT DEFAULT 0,
    avg_engagement NUMERIC(5,2) DEFAULT 0,
    added_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_competitor_profiles_user ON competitor_profiles(user_id);

CREATE TABLE IF NOT EXISTS competitor_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    period VARCHAR(10) NOT NULL,
    comparison JSONB DEFAULT '{}',
    content_gaps JSONB DEFAULT '[]',
    trending_topics JSONB DEFAULT '[]',
    generated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_competitor_reports_user ON competitor_reports(user_id);

-- 2. 해시태그 분석기
CREATE TABLE IF NOT EXISTS hashtag_performances (
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
CREATE INDEX IF NOT EXISTS idx_hashtag_performances_workspace ON hashtag_performances(workspace_id);

CREATE TABLE IF NOT EXISTS hashtag_groups (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    name            VARCHAR(100) NOT NULL,
    hashtags        JSONB NOT NULL DEFAULT '[]',
    platform        VARCHAR(20) NOT NULL,
    usage_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_hashtag_groups_workspace ON hashtag_groups(workspace_id);

-- 3. 해시태그 전략
CREATE TABLE IF NOT EXISTS hashtag_sets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    hashtags JSONB DEFAULT '[]',
    total_reach_estimate BIGINT DEFAULT 0,
    overall_difficulty VARCHAR(20),
    score INT DEFAULT 0,
    platform VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hashtag_sets_user ON hashtag_sets(user_id);

-- 4. 알고리즘 인사이트
CREATE TABLE IF NOT EXISTS algorithm_insights (
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
CREATE INDEX IF NOT EXISTS idx_algorithm_insights_workspace ON algorithm_insights(workspace_id);

CREATE TABLE IF NOT EXISTS algorithm_scores (
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
CREATE INDEX IF NOT EXISTS idx_algorithm_scores_workspace ON algorithm_scores(workspace_id);

CREATE TABLE IF NOT EXISTS algorithm_changes (
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
CREATE INDEX IF NOT EXISTS idx_algorithm_changes_workspace ON algorithm_changes(workspace_id);

-- 5. 캘린더 인사이트
CREATE TABLE IF NOT EXISTS calendar_insights (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    date            DATE NOT NULL,
    day_of_week     VARCHAR(10) NOT NULL,
    hour            INT NOT NULL,
    upload_count    INT NOT NULL DEFAULT 0,
    avg_views       BIGINT NOT NULL DEFAULT 0,
    avg_engagement  NUMERIC(5,2) NOT NULL DEFAULT 0,
    score           INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_calendar_insights_workspace ON calendar_insights(workspace_id);

CREATE TABLE IF NOT EXISTS optimal_time_slots (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    platform            VARCHAR(30),
    day_of_week         VARCHAR(10) NOT NULL,
    hour                INT NOT NULL,
    score               INT NOT NULL DEFAULT 0,
    expected_views      BIGINT NOT NULL DEFAULT 0,
    expected_engagement NUMERIC(5,2) NOT NULL DEFAULT 0,
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_optimal_time_slots_workspace ON optimal_time_slots(workspace_id);

CREATE TABLE IF NOT EXISTS upload_patterns (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    platform            VARCHAR(30) NOT NULL,
    total_uploads       INT NOT NULL DEFAULT 0,
    avg_uploads_per_week NUMERIC(5,2) NOT NULL DEFAULT 0,
    most_active_day     VARCHAR(10),
    most_active_hour    INT,
    consistency         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_upload_patterns_workspace ON upload_patterns(workspace_id);

-- 6. 채널 건강도
CREATE TABLE IF NOT EXISTS channel_health_metrics (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    channel_id          BIGINT NOT NULL,
    channel_name        VARCHAR(200) NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    overall_score       INT NOT NULL DEFAULT 0,
    growth_score        INT NOT NULL DEFAULT 0,
    engagement_score    INT NOT NULL DEFAULT 0,
    consistency_score   INT NOT NULL DEFAULT 0,
    audience_score      INT NOT NULL DEFAULT 0,
    monetization_score  INT NOT NULL DEFAULT 0,
    measured_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_channel_health_user ON channel_health_metrics(user_id);

CREATE TABLE IF NOT EXISTS health_trends (
    id              BIGSERIAL PRIMARY KEY,
    metric_id       BIGINT NOT NULL REFERENCES channel_health_metrics(id) ON DELETE CASCADE,
    category        VARCHAR(30) NOT NULL,
    trend_date      DATE NOT NULL,
    score           INT NOT NULL DEFAULT 0,
    change_value    NUMERIC(6,2) NOT NULL DEFAULT 0,
    recommendation  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_health_trends_metric ON health_trends(metric_id);
