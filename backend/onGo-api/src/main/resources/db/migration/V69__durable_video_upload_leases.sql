-- 외부 플랫폼 게시 작업의 재시작 안전성을 위한 durable queue 메타데이터.
ALTER TABLE video_uploads
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(120),
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS poll_token TEXT,
    ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_video_uploads_retry_queue
    ON video_uploads(status, next_retry_at)
    WHERE status IN ('UPLOADING', 'PROCESSING', 'UNCONFIRMED');

CREATE INDEX IF NOT EXISTS idx_video_uploads_lease_expiry
    ON video_uploads(lease_until)
    WHERE lease_until IS NOT NULL;
