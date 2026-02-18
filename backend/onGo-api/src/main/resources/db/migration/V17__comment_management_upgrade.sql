-- V17: Cross-platform comment management upgrade
-- Add columns to support platform comment sync, engagement, and filtering

ALTER TABLE comments ADD COLUMN IF NOT EXISTS like_count INT DEFAULT 0;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS reply_count INT DEFAULT 0;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT REFERENCES comments(id) ON DELETE SET NULL;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS platform_video_id VARCHAR(255);
ALTER TABLE comments ADD COLUMN IF NOT EXISTS platform_reply_id VARCHAR(255);
ALTER TABLE comments ADD COLUMN IF NOT EXISTS platform_like_id VARCHAR(255);
ALTER TABLE comments ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN DEFAULT FALSE;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN DEFAULT FALSE;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_channel_url VARCHAR(500);
ALTER TABLE comments ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP;

-- Deduplication: unique per platform + platform_comment_id
CREATE UNIQUE INDEX IF NOT EXISTS idx_comments_platform_dedup
    ON comments(platform, platform_comment_id) WHERE platform_comment_id IS NOT NULL;

-- Sync lookup by platform video
CREATE INDEX IF NOT EXISTS idx_comments_platform_video
    ON comments(platform_video_id) WHERE platform_video_id IS NOT NULL;

-- Parent comment lookup (replies tree)
CREATE INDEX IF NOT EXISTS idx_comments_parent
    ON comments(parent_comment_id) WHERE parent_comment_id IS NOT NULL;

-- Sync recency
CREATE INDEX IF NOT EXISTS idx_comments_synced_at
    ON comments(user_id, synced_at DESC);
