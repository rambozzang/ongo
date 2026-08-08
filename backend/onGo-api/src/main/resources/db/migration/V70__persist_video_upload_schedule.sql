ALTER TABLE video_uploads
    ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_video_uploads_due_schedule
    ON video_uploads(scheduled_at, status)
    WHERE scheduled_at IS NOT NULL;
