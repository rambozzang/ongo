-- Quality improvement: add missing indexes for N+1 query optimization
CREATE INDEX IF NOT EXISTS idx_video_uploads_video_id ON video_uploads(video_id);
