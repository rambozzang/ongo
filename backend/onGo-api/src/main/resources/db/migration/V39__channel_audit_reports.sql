-- V39: 채널 오디트 리포트 테이블
CREATE TABLE IF NOT EXISTS channel_audit_reports (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    overall_score INT NOT NULL DEFAULT 0,
    content       JSONB NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_channel_audit_reports_user ON channel_audit_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_channel_audit_reports_created ON channel_audit_reports(created_at DESC);
