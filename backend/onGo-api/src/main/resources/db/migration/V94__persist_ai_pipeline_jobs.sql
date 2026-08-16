CREATE TABLE IF NOT EXISTS ai_pipeline_jobs (
    id                    VARCHAR(64) PRIMARY KEY,
    user_id               BIGINT NOT NULL,
    video_id              BIGINT NOT NULL,
    channel_id            BIGINT,
    steps                 JSONB NOT NULL,
    current_step          VARCHAR(64),
    status                VARCHAR(32) NOT NULL,
    step_statuses         JSONB NOT NULL DEFAULT '{}'::jsonb,
    results               JSONB NOT NULL DEFAULT '{}'::jsonb,
    errors                JSONB NOT NULL DEFAULT '{}'::jsonb,
    total_credits_charged INTEGER NOT NULL,
    discount_applied      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMP,
    CONSTRAINT ai_pipeline_jobs_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ai_pipeline_jobs_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_pipeline_jobs_user_status
    ON ai_pipeline_jobs(user_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_pipeline_jobs_active
    ON ai_pipeline_jobs(status, updated_at ASC)
    WHERE status IN ('PENDING', 'RUNNING');
