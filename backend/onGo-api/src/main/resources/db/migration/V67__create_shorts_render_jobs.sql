CREATE TABLE IF NOT EXISTS shorts_render_jobs (
    id              VARCHAR(36) PRIMARY KEY,
    run_id          BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    clip_id         BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    progress        INTEGER,
    video_id        BIGINT REFERENCES videos(id) ON DELETE SET NULL,
    failure_reason  VARCHAR(1000),
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT ck_shorts_render_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_shorts_render_job_progress CHECK (progress IS NULL OR progress BETWEEN 0 AND 100),
    CONSTRAINT ck_shorts_render_job_attempts CHECK (attempt_count >= 0),
    CONSTRAINT uq_shorts_render_job_run_clip UNIQUE (run_id, clip_id)
);

CREATE INDEX IF NOT EXISTS idx_shorts_render_jobs_status_updated
    ON shorts_render_jobs(status, updated_at);
