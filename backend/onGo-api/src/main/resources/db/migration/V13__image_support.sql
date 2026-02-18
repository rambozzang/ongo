-- V13: Image/Photo Support for Multi-Platform Stories

-- 1. Add media_type enum
CREATE TYPE media_type AS ENUM ('VIDEO', 'IMAGE');

-- 2. Add media_type column to videos table (default VIDEO for backward compat)
ALTER TABLE videos ADD COLUMN IF NOT EXISTS media_type media_type NOT NULL DEFAULT 'VIDEO';

-- 3. content_images table — stores individual images within a post/story
CREATE TABLE content_images (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    image_url       TEXT NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    width           INT,
    height          INT,
    file_size_bytes BIGINT,
    original_filename TEXT,
    content_type    TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_content_images_video_id ON content_images(video_id);

-- 4. Index for media_type filtering
CREATE INDEX idx_videos_media_type ON videos(media_type);
