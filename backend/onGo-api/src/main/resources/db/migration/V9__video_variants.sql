-- ============================================================================
-- onGo - Creator Multi-Platform Management SaaS
-- Flyway Migration: V9__video_variants.sql
-- Platform-specific video variants (transcoded files)
-- ============================================================================

CREATE TYPE variant_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

CREATE TABLE video_variants (
    id BIGSERIAL PRIMARY KEY,
    video_id BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    platform platform_type NOT NULL,
    file_url TEXT,
    file_size_bytes BIGINT,
    width INT,
    height INT,
    bitrate_kbps INT,
    status variant_status NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (video_id, platform)
);
CREATE INDEX idx_video_variants_video_id ON video_variants(video_id);
