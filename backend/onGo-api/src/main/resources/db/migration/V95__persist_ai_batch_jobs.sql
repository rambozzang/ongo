CREATE TABLE IF NOT EXISTS ai_batch_jobs (
    id            VARCHAR(64) PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    operation     VARCHAR(64) NOT NULL,
    platform      VARCHAR(64),
    video_ids     JSONB NOT NULL,
    items         JSONB NOT NULL,
    total_items   INTEGER NOT NULL,
    status        VARCHAR(32) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_batch_jobs_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_batch_jobs_user_status
    ON ai_batch_jobs(user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_batch_jobs_active
    ON ai_batch_jobs(status, updated_at ASC)
    WHERE status IN ('PENDING', 'PROCESSING');
