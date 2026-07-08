-- 채널 헬스 메트릭 테이블
CREATE TABLE IF NOT EXISTS channel_health_metrics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    channel_name VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    overall_score INT DEFAULT 0,
    growth_score INT DEFAULT 0,
    engagement_score INT DEFAULT 0,
    consistency_score INT DEFAULT 0,
    audience_score INT DEFAULT 0,
    monetization_score INT DEFAULT 0,
    measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_channel_health_user_id ON channel_health_metrics(user_id);
CREATE INDEX IF NOT EXISTS idx_channel_health_channel_id ON channel_health_metrics(channel_id);

-- 헬스 트렌드 테이블
CREATE TABLE IF NOT EXISTS health_trends (
    id BIGSERIAL PRIMARY KEY,
    metric_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    trend_date DATE NOT NULL,
    score INT DEFAULT 0,
    change_value DECIMAL(10,2) DEFAULT 0,
    recommendation TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_health_trends_metric_id ON health_trends(metric_id);
CREATE INDEX IF NOT EXISTS idx_health_trends_metric_date ON health_trends(metric_id, trend_date);
