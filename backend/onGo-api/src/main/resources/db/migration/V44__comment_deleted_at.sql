-- 댓글 삭제 동기화를 위한 soft-delete 컬럼 추가
ALTER TABLE comments ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 삭제되지 않은 댓글 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_comments_deleted_at ON comments(deleted_at);
CREATE INDEX IF NOT EXISTS idx_comments_video_platform_not_deleted ON comments(video_id, platform) WHERE deleted_at IS NULL;
