-- 채널 건강도 대시보드
CREATE TABLE channel_health_metrics (
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

CREATE TABLE health_trends (
    id              BIGSERIAL PRIMARY KEY,
    metric_id       BIGINT NOT NULL REFERENCES channel_health_metrics(id) ON DELETE CASCADE,
    category        VARCHAR(30) NOT NULL,
    trend_date      DATE NOT NULL,
    score           INT NOT NULL DEFAULT 0,
    change_value    NUMERIC(6,2) NOT NULL DEFAULT 0,
    recommendation  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_channel_health_user ON channel_health_metrics(user_id);
CREATE INDEX idx_channel_health_platform ON channel_health_metrics(platform);
CREATE INDEX idx_health_trends_metric ON health_trends(metric_id);
