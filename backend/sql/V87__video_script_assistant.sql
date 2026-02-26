-- AI 비디오 스크립트 어시스턴트
CREATE TABLE video_scripts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    video_id        BIGINT REFERENCES videos(id),
    video_title     VARCHAR(300),
    content         TEXT NOT NULL DEFAULT '',
    tone            VARCHAR(30) NOT NULL DEFAULT 'CASUAL',
    target_length   INT NOT NULL DEFAULT 500,
    word_count      INT NOT NULL DEFAULT 0,
    hook_line       VARCHAR(500),
    cta_text        VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE script_suggestions (
    id              BIGSERIAL PRIMARY KEY,
    script_id       BIGINT NOT NULL REFERENCES video_scripts(id) ON DELETE CASCADE,
    section_type    VARCHAR(30) NOT NULL,
    original_text   TEXT NOT NULL,
    suggested_text  TEXT NOT NULL,
    reason          TEXT NOT NULL,
    is_applied      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_video_scripts_user ON video_scripts(user_id);
CREATE INDEX idx_video_scripts_status ON video_scripts(status);
CREATE INDEX idx_script_suggestions_script ON script_suggestions(script_id);
