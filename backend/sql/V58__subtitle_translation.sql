-- Wave 15: 자동 자막 번역
CREATE TABLE subtitle_translations (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    video_title         VARCHAR(300) NOT NULL,
    source_language     VARCHAR(10) NOT NULL,
    target_language     VARCHAR(10) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress            INT NOT NULL DEFAULT 0,
    source_line_count   INT NOT NULL DEFAULT 0,
    translated_line_count INT NOT NULL DEFAULT 0,
    quality             INT NOT NULL DEFAULT 0,
    cost                INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE translation_lines (
    id              BIGSERIAL PRIMARY KEY,
    translation_id  BIGINT NOT NULL REFERENCES subtitle_translations(id) ON DELETE CASCADE,
    line_number     INT NOT NULL,
    start_time      VARCHAR(20) NOT NULL,
    end_time        VARCHAR(20) NOT NULL,
    source_text     TEXT NOT NULL,
    translated_text TEXT NOT NULL DEFAULT '',
    is_edited       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE supported_languages (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(10) NOT NULL UNIQUE,
    name            VARCHAR(50) NOT NULL,
    native_name     VARCHAR(50) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subtitle_translations_workspace ON subtitle_translations(workspace_id);
CREATE INDEX idx_subtitle_translations_status ON subtitle_translations(workspace_id, status);
CREATE INDEX idx_translation_lines_translation ON translation_lines(translation_id);
CREATE INDEX idx_translation_lines_order ON translation_lines(translation_id, line_number);
