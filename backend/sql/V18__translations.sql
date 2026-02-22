-- V18__translations.sql
-- 다국어 콘텐츠 자동화

CREATE TABLE video_translations (
    id               BIGSERIAL PRIMARY KEY,
    video_id         BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    language         VARCHAR(10) NOT NULL,  -- ko, en, ja, zh, es, fr, de, pt
    title            TEXT,
    description      TEXT,
    tags             JSONB DEFAULT '[]',
    subtitle_content TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, TRANSLATING, COMPLETED, FAILED
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_translations_video ON video_translations(video_id);
CREATE UNIQUE INDEX idx_video_translations_unique ON video_translations(video_id, language);
