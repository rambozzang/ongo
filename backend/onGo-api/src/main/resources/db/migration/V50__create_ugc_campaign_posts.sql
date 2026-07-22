-- UGC 유료 파일럿 MVP — Sprint 4 (V50): 멀티 SNS 게시 연결
-- 승인된 제출물을 기존 게시 흐름으로 게시(DIRECT)하거나 외부 게시물 URL을 등록(EXTERNAL)하고
-- 플랫폼별 결과를 campaign post로 추적한다. idempotency_key로 재시도 중복 게시를 방지한다.

CREATE TABLE IF NOT EXISTS ugc_campaign_posts (
    id                BIGSERIAL PRIMARY KEY,
    campaign_id       BIGINT NOT NULL REFERENCES ugc_campaigns(id) ON DELETE CASCADE,
    submission_id     BIGINT NOT NULL REFERENCES ugc_content_submissions(id) ON DELETE CASCADE,
    creator_id        BIGINT NOT NULL,
    platform          VARCHAR(30) NOT NULL,
    post_type         VARCHAR(20) NOT NULL,
    video_upload_id   BIGINT,
    external_post_url VARCHAR(1000),
    platform_post_id  VARCHAR(255),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(200) NOT NULL,
    error_message     TEXT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_posts_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_ugc_posts_campaign ON ugc_campaign_posts(campaign_id);
CREATE INDEX IF NOT EXISTS idx_ugc_posts_submission ON ugc_campaign_posts(submission_id);

-- 외부 게시물 중복 방지 (플랫폼 게시물 ID가 있을 때만)
CREATE UNIQUE INDEX IF NOT EXISTS uq_ugc_posts_platform_post
    ON ugc_campaign_posts(campaign_id, platform, platform_post_id)
    WHERE platform_post_id IS NOT NULL;
