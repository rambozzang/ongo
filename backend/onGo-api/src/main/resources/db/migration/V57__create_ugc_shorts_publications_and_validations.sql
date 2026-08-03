CREATE TABLE IF NOT EXISTS ugc_shorts_clip_publications (
    id              BIGSERIAL PRIMARY KEY,
    clip_id         BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    platform        VARCHAR(30) NOT NULL,
    video_upload_id BIGINT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    scheduled_at    TIMESTAMP,
    published_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_clip_publications UNIQUE (clip_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_ugc_shorts_clip_publications_clip
    ON ugc_shorts_clip_publications(clip_id);

CREATE TABLE IF NOT EXISTS ugc_shorts_validations (
    id         BIGSERIAL PRIMARY KEY,
    clip_id    BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    rule_code  VARCHAR(50) NOT NULL,
    severity   VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    passed     BOOLEAN NOT NULL DEFAULT TRUE,
    message    TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ugc_shorts_validations_clip
    ON ugc_shorts_validations(clip_id);
