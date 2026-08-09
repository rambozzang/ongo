ALTER TABLE public_api_posts
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_public_api_posts_user_idempotency
    ON public_api_posts(user_id, idempotency_key);
