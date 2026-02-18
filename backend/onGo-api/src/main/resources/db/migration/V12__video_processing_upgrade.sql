-- V12: Video Processing Upgrade
-- FFprobe media info, subtitles, processing progress, enhanced variants

-- 1. video_media_info: FFprobe 분석 결과 저장
CREATE TABLE video_media_info (
    id            BIGSERIAL PRIMARY KEY,
    video_id      BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    -- Video stream
    video_codec   TEXT,
    width         INT,
    height        INT,
    fps           DOUBLE PRECISION,
    bitrate_kbps  INT,
    duration_ms   BIGINT,
    color_space   TEXT,
    pixel_format  TEXT,
    profile       TEXT,
    -- Audio stream
    audio_codec   TEXT,
    audio_bitrate_kbps INT,
    sample_rate   INT,
    audio_channels INT,
    -- Container
    format_name   TEXT,
    file_size_bytes BIGINT,
    -- Raw FFprobe output
    raw_json      JSONB,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (video_id)
);

-- 2. video_subtitles: 자막 저장
CREATE TABLE video_subtitles (
    id                  BIGSERIAL PRIMARY KEY,
    video_id            BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    language            TEXT NOT NULL DEFAULT 'ko',
    format              TEXT NOT NULL DEFAULT 'srt',
    content             TEXT NOT NULL,
    is_auto_generated   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (video_id, language)
);

-- 3. video_processing_progress: 처리 진행률
CREATE TYPE processing_stage AS ENUM ('PROBE', 'TRANSCODE', 'THUMBNAIL', 'CAPTION', 'UPLOAD');

CREATE TABLE video_processing_progress (
    id               BIGSERIAL PRIMARY KEY,
    video_id         BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    stage            processing_stage NOT NULL,
    platform         platform_type,
    progress_percent INT NOT NULL DEFAULT 0,
    message          TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (video_id, stage, platform)
);

-- 4. video_variants 테이블 확장
ALTER TABLE video_variants ADD COLUMN IF NOT EXISTS fps DOUBLE PRECISION;
ALTER TABLE video_variants ADD COLUMN IF NOT EXISTS crf INT;
ALTER TABLE video_variants ADD COLUMN IF NOT EXISTS preset TEXT;
ALTER TABLE video_variants ADD COLUMN IF NOT EXISTS encoding_profile TEXT;
ALTER TABLE video_variants ADD COLUMN IF NOT EXISTS encoding_time_ms BIGINT;
ALTER TABLE video_variants ADD COLUMN IF NOT EXISTS skipped BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. videos 테이블 확장
ALTER TABLE videos ADD COLUMN IF NOT EXISTS auto_thumbnails JSONB DEFAULT '[]'::jsonb;
ALTER TABLE videos ADD COLUMN IF NOT EXISTS selected_thumbnail_index INT DEFAULT 0;

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_video_media_info_video_id ON video_media_info(video_id);
CREATE INDEX IF NOT EXISTS idx_video_subtitles_video_id ON video_subtitles(video_id);
CREATE INDEX IF NOT EXISTS idx_video_processing_progress_video_id ON video_processing_progress(video_id);
