-- V13__deep_analytics.sql

-- analytics_daily에 노출수/평균시청시간 추가
ALTER TABLE analytics_daily ADD COLUMN IF NOT EXISTS impressions INTEGER DEFAULT 0;
ALTER TABLE analytics_daily ADD COLUMN IF NOT EXISTS avg_view_duration_seconds INTEGER DEFAULT 0;

-- 채널 수준 인사이트 (트래픽 소스, 인구통계) - 일별 스냅샷
CREATE TABLE channel_insights_daily (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users(id),
    platform              VARCHAR(20) NOT NULL,
    date                  DATE NOT NULL,
    traffic_source        JSONB DEFAULT '{}',
    demographics_age      JSONB DEFAULT '{}',
    demographics_gender   JSONB DEFAULT '{}',
    demographics_country  JSONB DEFAULT '{}',
    created_at            TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_channel_insights_daily_unique
    ON channel_insights_daily(user_id, platform, date);
CREATE INDEX idx_channel_insights_daily_date
    ON channel_insights_daily(date);
