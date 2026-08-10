ALTER TABLE ugc_shorts_pipeline_runs
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ugc_shorts_runs_user_idempotency
    ON ugc_shorts_pipeline_runs(user_id, idempotency_key);
