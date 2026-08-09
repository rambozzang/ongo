ALTER TABLE ugc_shorts_pipeline_runs
    ADD COLUMN IF NOT EXISTS auto_schedule BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_schedule_start_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS auto_schedule_interval_hours INTEGER,
    ADD COLUMN IF NOT EXISTS auto_schedule_platforms TEXT;

CREATE INDEX IF NOT EXISTS idx_ugc_shorts_auto_schedule_queue
    ON ugc_shorts_pipeline_runs(status, auto_schedule, updated_at)
    WHERE auto_schedule = TRUE;
