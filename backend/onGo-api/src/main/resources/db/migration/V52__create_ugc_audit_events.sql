-- UGC 유료 파일럿 MVP — Sprint 6 (V52): 감사 로그
-- 캠페인/정산 등 주요 상태 변경을 운영자가 시간순으로 조회할 수 있도록 기록한다.

CREATE TABLE IF NOT EXISTS ugc_audit_events (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT NOT NULL,
    campaign_id   BIGINT,
    actor_id      BIGINT NOT NULL,
    action        VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30),
    resource_id   BIGINT,
    detail        TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ugc_audit_campaign ON ugc_audit_events(campaign_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ugc_audit_workspace ON ugc_audit_events(workspace_id, created_at DESC);
