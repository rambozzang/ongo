-- UGC 유료 파일럿 MVP — Sprint 5 (V51): 게시물 지표 스냅샷 + 보상 확정
-- 감사 로그(ugc_audit_events)는 Sprint 6에서 별도 마이그레이션으로 추가한다.
-- 지표는 원본 스냅샷으로 저장하고 캠페인 대시보드는 게시물별 최신 스냅샷을 합산한다.
-- 보상은 참여자당 1건이며 확정 총액이 캠페인 예산을 넘지 않도록 애플리케이션에서 검증한다.

CREATE TABLE IF NOT EXISTS ugc_post_metric_snapshots (
    id               BIGSERIAL PRIMARY KEY,
    campaign_post_id BIGINT NOT NULL REFERENCES ugc_campaign_posts(id) ON DELETE CASCADE,
    captured_at      TIMESTAMP NOT NULL,
    views            BIGINT NOT NULL DEFAULT 0,
    likes            BIGINT NOT NULL DEFAULT 0,
    comments         BIGINT NOT NULL DEFAULT 0,
    shares           BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_metric_snapshot UNIQUE (campaign_post_id, captured_at)
);
CREATE INDEX IF NOT EXISTS idx_ugc_metric_snapshots_post ON ugc_post_metric_snapshots(campaign_post_id, captured_at DESC);

CREATE TABLE IF NOT EXISTS ugc_reward_confirmations (
    id            BIGSERIAL PRIMARY KEY,
    participant_id BIGINT NOT NULL REFERENCES ugc_campaign_participants(id) ON DELETE CASCADE,
    campaign_id   BIGINT NOT NULL,
    creator_id    BIGINT NOT NULL,
    base_amount   BIGINT NOT NULL DEFAULT 0,
    bonus_amount  BIGINT NOT NULL DEFAULT 0,
    total_amount  BIGINT NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note          TEXT,
    confirmed_by  BIGINT,
    confirmed_at  TIMESTAMP,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_reward_participant UNIQUE (participant_id),
    CONSTRAINT chk_ugc_reward_nonneg CHECK (base_amount >= 0 AND bonus_amount >= 0 AND total_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_ugc_rewards_campaign ON ugc_reward_confirmations(campaign_id, status);
