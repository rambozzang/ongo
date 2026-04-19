-- 수익 인텔리전스: 알림 설정 + AI 인사이트

-- 수익 알림 설정 테이블
CREATE TABLE revenue_alert_configs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    alert_type      VARCHAR(50) NOT NULL,
    threshold_value NUMERIC,
    schedule_time   TIME DEFAULT '09:00',
    is_enabled      BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 수익 AI 인사이트 테이블
CREATE TABLE revenue_insights (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    insight_type    VARCHAR(50) NOT NULL,
    content         JSONB NOT NULL,
    platform        VARCHAR(30),
    confidence      NUMERIC(3,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 인덱스
CREATE INDEX idx_revenue_alert_configs_user ON revenue_alert_configs(user_id);
CREATE INDEX idx_revenue_insights_user_created ON revenue_insights(user_id, created_at DESC);
