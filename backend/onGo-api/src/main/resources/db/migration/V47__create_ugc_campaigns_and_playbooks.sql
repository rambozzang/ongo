-- UGC 유료 파일럿 MVP — Sprint 1 (V47): 캠페인 + 플레이북
-- ID 관례: BIGSERIAL PK / BIGINT FK (기존 스키마와 동일, UUID 미사용)
-- 금액: 최소 화폐 단위 BIGINT (KRW는 원 단위)
-- 비열거성이 필요한 초대는 원문 대신 token_hash 만 저장

CREATE TABLE IF NOT EXISTS ugc_campaigns (
    id                       BIGSERIAL PRIMARY KEY,
    workspace_id             BIGINT NOT NULL,
    name                     VARCHAR(150) NOT NULL,
    description              TEXT,
    status                   VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    objective                VARCHAR(50) NOT NULL DEFAULT 'AWARENESS',
    total_budget             BIGINT NOT NULL DEFAULT 0,
    currency                 VARCHAR(3) NOT NULL DEFAULT 'KRW',
    fixed_reward_per_creator BIGINT NOT NULL DEFAULT 0,
    start_at                 TIMESTAMP,
    end_at                   TIMESTAMP,
    created_by               BIGINT NOT NULL,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ugc_campaigns_budget_nonneg CHECK (total_budget >= 0),
    CONSTRAINT chk_ugc_campaigns_reward_nonneg CHECK (fixed_reward_per_creator >= 0),
    CONSTRAINT chk_ugc_campaigns_period CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
);
CREATE INDEX IF NOT EXISTS idx_ugc_campaigns_workspace ON ugc_campaigns(workspace_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS ugc_playbooks (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    summary      TEXT,
    content_type VARCHAR(30) NOT NULL DEFAULT 'UGC_VIDEO',
    revision     INT NOT NULL DEFAULT 1,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- MVP: 캠페인당 활성 플레이북 1개
CREATE UNIQUE INDEX IF NOT EXISTS uq_ugc_playbooks_campaign ON ugc_playbooks(campaign_id);

CREATE TABLE IF NOT EXISTS ugc_playbook_steps (
    id          BIGSERIAL PRIMARY KEY,
    playbook_id BIGINT NOT NULL REFERENCES ugc_playbooks(id) ON DELETE CASCADE,
    sort_order  INT NOT NULL,
    step_type   VARCHAR(30) NOT NULL DEFAULT 'INSTRUCTION',
    title       VARCHAR(200) NOT NULL,
    instruction TEXT,
    example_url VARCHAR(500),
    required    BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_ugc_playbook_steps_order UNIQUE (playbook_id, sort_order)
);
CREATE INDEX IF NOT EXISTS idx_ugc_playbook_steps_playbook ON ugc_playbook_steps(playbook_id);

CREATE TABLE IF NOT EXISTS ugc_campaign_rules (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    rule_type   VARCHAR(30) NOT NULL,
    value       TEXT NOT NULL,
    required    BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ugc_campaign_rules_campaign ON ugc_campaign_rules(campaign_id);

-- 초대 링크 (API는 Sprint 2에서 구현, 테이블은 V47에 선반영)
CREATE TABLE IF NOT EXISTS ugc_campaign_invites (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP,
    max_uses    INT,
    used_count  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_campaign_invites_token UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS idx_ugc_campaign_invites_campaign ON ugc_campaign_invites(campaign_id);
