-- V85: Live Dashboard Tables
-- 라이브 대시보드 알림 및 알림 설정 테이블

CREATE TABLE IF NOT EXISTS live_dashboard_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL DEFAULT '',
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_live_dashboard_alerts_user_id ON live_dashboard_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_live_dashboard_alerts_is_read ON live_dashboard_alerts(user_id, is_read);

CREATE TABLE IF NOT EXISTS live_alert_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    threshold INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_live_alert_configs_user_id ON live_alert_configs(user_id);
