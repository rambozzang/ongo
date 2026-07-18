-- UGC 유료 파일럿 MVP — Sprint 3 (V49): 콘텐츠 제출·에셋·검수
-- 게시(ugc_campaign_posts)는 Sprint 4에서 V50으로 추가한다(Flyway 불변성: 스프린트별 분리).
-- 제출은 참여자(캠페인+크리에이터)당 1건, revision 카운터로 재제출 이력을 표현하고
-- 모든 검수 판단(사용자·시각·사유)은 ugc_submission_reviews에 남긴다.

CREATE TABLE IF NOT EXISTS ugc_content_submissions (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    creator_id   BIGINT NOT NULL,
    revision     INT NOT NULL DEFAULT 1,
    caption      TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP,
    approved_at  TIMESTAMP,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_submissions_campaign_creator UNIQUE (campaign_id, creator_id)
);
CREATE INDEX IF NOT EXISTS idx_ugc_submissions_campaign ON ugc_content_submissions(campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_ugc_submissions_creator ON ugc_content_submissions(creator_id);

CREATE TABLE IF NOT EXISTS ugc_submission_assets (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES ugc_content_submissions(id) ON DELETE CASCADE,
    asset_type    VARCHAR(20) NOT NULL,
    resource_type VARCHAR(20),
    resource_id   BIGINT,
    external_url  VARCHAR(1000),
    sort_order    INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ugc_submission_assets_submission ON ugc_submission_assets(submission_id);

CREATE TABLE IF NOT EXISTS ugc_submission_reviews (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES ugc_content_submissions(id) ON DELETE CASCADE,
    reviewer_id   BIGINT NOT NULL,
    decision      VARCHAR(20) NOT NULL,
    comment       TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ugc_submission_reviews_submission ON ugc_submission_reviews(submission_id, created_at);
