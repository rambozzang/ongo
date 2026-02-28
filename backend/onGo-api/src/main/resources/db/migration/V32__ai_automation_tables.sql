-- V32: AI/자동화 테이블
-- automation, ai_calendar, calendar_ai, growth, schedule, trend, script_assist, seo

-- 1. 플랫폼 자동화
CREATE TABLE IF NOT EXISTS automation_rules (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL,
    action_type     VARCHAR(50) NOT NULL,
    condition_text  TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    execution_count INT NOT NULL DEFAULT 0,
    last_executed   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_auto_rules_user ON automation_rules(user_id);

CREATE TABLE IF NOT EXISTS automation_logs (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    rule_name       VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    message         TEXT,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_auto_logs_rule ON automation_logs(rule_id);

-- 2. AI 캘린더
CREATE TABLE IF NOT EXISTS ai_content_calendars (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    settings JSONB,
    calendar_data JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. AI 콘텐츠 캘린더 제안
CREATE TABLE IF NOT EXISTS calendar_suggestions (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    suggested_date  DATE NOT NULL,
    suggested_time  VARCHAR(10) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    topic           VARCHAR(200),
    expected_engagement NUMERIC(5,2) DEFAULT 0,
    confidence      INT DEFAULT 0,
    status          VARCHAR(30) DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_calendar_suggestions_workspace ON calendar_suggestions(workspace_id);

CREATE TABLE IF NOT EXISTS calendar_ai_slots (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    slot_date       DATE NOT NULL,
    slot_time       VARCHAR(10) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    score           INT DEFAULT 0,
    reason          TEXT,
    created_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_calendar_ai_slots_workspace ON calendar_ai_slots(workspace_id);

-- 4. 성장 코치
CREATE TABLE IF NOT EXISTS growth_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    target_value BIGINT NOT NULL,
    current_value BIGINT DEFAULT 0,
    deadline VARCHAR(20),
    progress INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_growth_goals_user ON growth_goals(user_id);

CREATE TABLE IF NOT EXISTS weekly_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    week_start VARCHAR(20) NOT NULL,
    week_end VARCHAR(20) NOT NULL,
    summary TEXT,
    highlights JSONB DEFAULT '[]',
    concerns JSONB DEFAULT '[]',
    subscriber_growth INT DEFAULT 0,
    views_growth INT DEFAULT 0,
    engagement_change DECIMAL(5,2) DEFAULT 0,
    top_content JSONB DEFAULT '[]',
    action_items JSONB DEFAULT '[]',
    overall_score INT DEFAULT 0,
    generated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_weekly_reports_user ON weekly_reports(user_id);

-- 5. 일정 최적화
CREATE TABLE IF NOT EXISTS optimal_slots (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    platform            VARCHAR(50) NOT NULL,
    day_of_week         VARCHAR(20) NOT NULL,
    hour                INT NOT NULL,
    score               INT NOT NULL DEFAULT 0,
    audience_online     INT NOT NULL DEFAULT 0,
    competition_level   VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    reason              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_optimal_slots_user ON optimal_slots(user_id);

CREATE TABLE IF NOT EXISTS schedule_recommendations (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id),
    video_id                BIGINT NOT NULL,
    video_title             VARCHAR(300) NOT NULL,
    current_schedule        TIMESTAMPTZ,
    recommended_schedule    TIMESTAMPTZ NOT NULL,
    platform                VARCHAR(50) NOT NULL,
    expected_improvement    INT NOT NULL DEFAULT 0,
    confidence              INT NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_schedule_recs_user ON schedule_recommendations(user_id);

-- 6. 트렌드 예측기
CREATE TABLE IF NOT EXISTS trend_predictions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    keyword         VARCHAR(200) NOT NULL,
    category        VARCHAR(100) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    current_score   INT NOT NULL DEFAULT 0,
    predicted_score INT NOT NULL DEFAULT 0,
    confidence      NUMERIC(5,2) NOT NULL DEFAULT 0,
    direction       VARCHAR(20) NOT NULL DEFAULT 'STABLE',
    peak_date       DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_trend_predictions_user ON trend_predictions(user_id);

CREATE TABLE IF NOT EXISTS trend_topics (
    id              BIGSERIAL PRIMARY KEY,
    prediction_id   BIGINT NOT NULL REFERENCES trend_predictions(id) ON DELETE CASCADE,
    topic           VARCHAR(200) NOT NULL,
    related_keywords JSONB NOT NULL DEFAULT '[]',
    video_count     INT NOT NULL DEFAULT 0,
    avg_views       BIGINT NOT NULL DEFAULT 0,
    growth_rate     NUMERIC(7,2) NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_trend_topics_prediction ON trend_topics(prediction_id);

-- 7. 비디오 스크립트 어시스턴트
CREATE TABLE IF NOT EXISTS video_scripts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    video_id        BIGINT REFERENCES videos(id),
    video_title     VARCHAR(300),
    content         TEXT NOT NULL DEFAULT '',
    tone            VARCHAR(30) NOT NULL DEFAULT 'CASUAL',
    target_length   INT NOT NULL DEFAULT 500,
    word_count      INT NOT NULL DEFAULT 0,
    hook_line       VARCHAR(500),
    cta_text        VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_video_scripts_user ON video_scripts(user_id);

CREATE TABLE IF NOT EXISTS script_suggestions (
    id              BIGSERIAL PRIMARY KEY,
    script_id       BIGINT NOT NULL REFERENCES video_scripts(id) ON DELETE CASCADE,
    section_type    VARCHAR(30) NOT NULL,
    original_text   TEXT NOT NULL,
    suggested_text  TEXT NOT NULL,
    reason          TEXT NOT NULL,
    is_applied      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_script_suggestions_script ON script_suggestions(script_id);

-- 8. 비디오 SEO
CREATE TABLE IF NOT EXISTS seo_analyses (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    overall_score       INT DEFAULT 0,
    title_score         INT DEFAULT 0,
    description_score   INT DEFAULT 0,
    tag_score           INT DEFAULT 0,
    thumbnail_score     INT DEFAULT 0,
    suggestions         JSONB DEFAULT '[]',
    analyzed_at         TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_seo_analyses_workspace ON seo_analyses(workspace_id);

CREATE TABLE IF NOT EXISTS seo_keywords (
    id                  BIGSERIAL PRIMARY KEY,
    analysis_id         BIGINT NOT NULL REFERENCES seo_analyses(id) ON DELETE CASCADE,
    keyword             VARCHAR(300) NOT NULL,
    search_volume       BIGINT DEFAULT 0,
    competition         VARCHAR(30) DEFAULT 'MEDIUM',
    relevance           INT DEFAULT 0,
    trend               VARCHAR(30) DEFAULT 'STABLE',
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_seo_keywords_analysis ON seo_keywords(analysis_id);
