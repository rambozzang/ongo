-- UGC 유료 파일럿 MVP — Sprint 2 (V48): 지원(application)과 참여(participant)
-- 제출/검수/게시 테이블은 Sprint 3에서 V49로 추가한다(Flyway 마이그레이션 불변성: 스프린트별 분리).

CREATE TABLE IF NOT EXISTS ugc_campaign_applications (
    id            BIGSERIAL PRIMARY KEY,
    campaign_id   BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    creator_id    BIGINT NOT NULL,
    message       TEXT,
    portfolio_url VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    decided_by    BIGINT,
    decided_at    TIMESTAMP,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_applications_campaign_creator UNIQUE (campaign_id, creator_id)
);
CREATE INDEX IF NOT EXISTS idx_ugc_applications_campaign ON ugc_campaign_applications(campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_ugc_applications_creator ON ugc_campaign_applications(creator_id);

CREATE TABLE IF NOT EXISTS ugc_campaign_participants (
    id            BIGSERIAL PRIMARY KEY,
    campaign_id   BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    creator_id    BIGINT NOT NULL,
    agreed_reward BIGINT NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_participants_campaign_creator UNIQUE (campaign_id, creator_id),
    CONSTRAINT chk_ugc_participants_reward_nonneg CHECK (agreed_reward >= 0)
);
CREATE INDEX IF NOT EXISTS idx_ugc_participants_campaign ON ugc_campaign_participants(campaign_id);
CREATE INDEX IF NOT EXISTS idx_ugc_participants_creator ON ugc_campaign_participants(creator_id);
