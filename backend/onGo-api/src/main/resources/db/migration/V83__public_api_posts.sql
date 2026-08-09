CREATE TABLE IF NOT EXISTS public_api_posts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id     BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    post_type    VARCHAR(16) NOT NULL,
    status       VARCHAR(32) NOT NULL,
    scheduled_at TIMESTAMP NULL,
    payload_json TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_public_api_post_type CHECK (post_type IN ('NOW', 'SCHEDULE', 'DRAFT')),
    CONSTRAINT ck_public_api_post_status CHECK (
        status IN ('DRAFT', 'PROCESSING', 'SCHEDULED', 'PUBLISHED',
                   'PARTIALLY_PUBLISHED', 'UNCONFIRMED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_public_api_posts_user_created
    ON public_api_posts(user_id, created_at DESC);
