-- V28: 분석 후반 테이블
-- platform_health, performance_report, quality, cluster, overlap, fan_insights

-- 1. 플랫폼 건강 점수
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
CREATE INDEX IF NOT EXISTS idx_platform_health_workspace ON platform_health_scores(workspace_id);

CREATE TABLE IF NOT EXISTS health_issues (
    id                  BIGSERIAL PRIMARY KEY,
    health_score_id     BIGINT NOT NULL REFERENCES platform_health_scores(id) ON DELETE CASCADE,
    severity            VARCHAR(30) DEFAULT 'LOW',
    category            VARCHAR(100) NOT NULL,
    description         TEXT NOT NULL,
    recommendation      TEXT,
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_health_issues_score ON health_issues(health_score_id);

-- 2. 콘텐츠 성과 보고서
CREATE TABLE IF NOT EXISTS performance_reports (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    title               VARCHAR(300) NOT NULL,
    period              VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_views         BIGINT NOT NULL DEFAULT 0,
    total_engagement    BIGINT NOT NULL DEFAULT 0,
    top_video_id        BIGINT,
    top_video_title     VARCHAR(500),
    report_url          VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_perf_reports_user ON performance_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_perf_reports_status ON performance_reports(status);

CREATE TABLE IF NOT EXISTS report_sections (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL REFERENCES performance_reports(id) ON DELETE CASCADE,
    section_type    VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    chart_data      JSONB,
    sort_order      INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_report_sections_report ON report_sections(report_id);

-- 3. 품질 점수
CREATE TABLE IF NOT EXISTS quality_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT,
    video_title VARCHAR(500),
    overall_score INT NOT NULL DEFAULT 0,
    overall_grade VARCHAR(10) NOT NULL DEFAULT 'F',
    metrics JSONB DEFAULT '{}',
    improvements JSONB DEFAULT '[]',
    competitor_avg INT DEFAULT 0,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quality_reports_user_id ON quality_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_quality_reports_video_id ON quality_reports(video_id);

-- 4. 콘텐츠 클러스터
CREATE TABLE IF NOT EXISTS content_clusters (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    content_count   INT DEFAULT 0,
    avg_views       BIGINT DEFAULT 0,
    avg_engagement  NUMERIC(5,2) DEFAULT 0,
    top_content     VARCHAR(500),
    tags            JSONB DEFAULT '[]',
    platform        VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_content_clusters_workspace ON content_clusters(workspace_id);

CREATE TABLE IF NOT EXISTS cluster_contents (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT NOT NULL REFERENCES content_clusters(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    views           BIGINT DEFAULT 0,
    likes           INT DEFAULT 0,
    engagement      NUMERIC(5,2) DEFAULT 0,
    published_at    TIMESTAMP,
    similarity      NUMERIC(4,3) DEFAULT 0,
    created_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cluster_contents_cluster ON cluster_contents(cluster_id);

-- 5. 오디언스 오버랩
CREATE TABLE IF NOT EXISTS audience_overlap_results (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    platform_a          VARCHAR(50) NOT NULL,
    platform_b          VARCHAR(50) NOT NULL,
    overlap_percent     NUMERIC(5,2) DEFAULT 0,
    unique_to_a         BIGINT DEFAULT 0,
    unique_to_b         BIGINT DEFAULT 0,
    shared_audience     BIGINT DEFAULT 0,
    analyzed_at         TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audience_overlap_workspace ON audience_overlap_results(workspace_id);

CREATE TABLE IF NOT EXISTS overlap_segments (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    name                VARCHAR(200) NOT NULL,
    platforms           JSONB DEFAULT '[]',
    audience_size       BIGINT DEFAULT 0,
    engagement_rate     NUMERIC(5,2) DEFAULT 0,
    top_interest        VARCHAR(200),
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_overlap_segments_workspace ON overlap_segments(workspace_id);

-- 6. 팬 인사이트
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
CREATE INDEX IF NOT EXISTS idx_fan_demographics_workspace ON fan_demographics(workspace_id);

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
CREATE INDEX IF NOT EXISTS idx_fan_behaviors_workspace ON fan_behaviors(workspace_id);

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
CREATE INDEX IF NOT EXISTS idx_fan_segments_workspace ON fan_segments(workspace_id);
