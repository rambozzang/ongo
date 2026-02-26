-- 콘텐츠 번역기
CREATE TABLE translation_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    video_id            BIGINT NOT NULL,
    video_title         VARCHAR(300) NOT NULL,
    source_language     VARCHAR(10) NOT NULL,
    target_language     VARCHAR(10) NOT NULL,
    content_type        VARCHAR(30) NOT NULL DEFAULT 'ALL',
    original_text       TEXT NOT NULL,
    translated_text     TEXT,
    quality             INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

CREATE TABLE translation_glossary (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    source_word         VARCHAR(200) NOT NULL,
    target_word         VARCHAR(200) NOT NULL,
    source_language     VARCHAR(10) NOT NULL,
    target_language     VARCHAR(10) NOT NULL,
    context             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_translation_jobs_user ON translation_jobs(user_id);
CREATE INDEX idx_translation_jobs_status ON translation_jobs(status);
CREATE INDEX idx_translation_glossary_user ON translation_glossary(user_id);
CREATE INDEX idx_translation_glossary_lang ON translation_glossary(source_language, target_language);
