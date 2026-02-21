-- V12__competitor_analytics.sql
CREATE TABLE competitor_analytics_daily (
    id                    BIGSERIAL PRIMARY KEY,
    competitor_id         BIGINT NOT NULL REFERENCES competitors(id) ON DELETE CASCADE,
    date                  DATE NOT NULL,
    subscriber_count      BIGINT DEFAULT 0,
    video_count           INTEGER DEFAULT 0,
    avg_views             BIGINT DEFAULT 0,
    avg_likes             BIGINT DEFAULT 0,
    avg_comments          BIGINT DEFAULT 0,
    total_views           BIGINT DEFAULT 0,
    created_at            TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_competitor_analytics_daily_unique
    ON competitor_analytics_daily(competitor_id, date);
CREATE INDEX idx_competitor_analytics_daily_date
    ON competitor_analytics_daily(date);
