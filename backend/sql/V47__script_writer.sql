-- AI 스크립트 작성기
CREATE TABLE scripts (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    title           VARCHAR(200)    NOT NULL,
    topic           VARCHAR(300)    NOT NULL,
    format          VARCHAR(30)     NOT NULL DEFAULT 'LONG_FORM',
    tone            VARCHAR(30)     NOT NULL DEFAULT 'CASUAL',
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    target_duration INT             NOT NULL DEFAULT 600,
    estimated_word_count INT        NOT NULL DEFAULT 0,
    keywords        TEXT[]          DEFAULT '{}',
    target_audience VARCHAR(200),
    notes           TEXT,
    credit_cost     INT             NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE script_sections (
    id              BIGSERIAL       PRIMARY KEY,
    script_id       BIGINT          NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    type            VARCHAR(20)     NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    content         TEXT            NOT NULL DEFAULT '',
    duration        INT             NOT NULL DEFAULT 0,
    notes           TEXT,
    order_number    INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE script_templates (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES users(id),
    name            VARCHAR(200)    NOT NULL,
    description     TEXT,
    format          VARCHAR(30)     NOT NULL,
    tone            VARCHAR(30)     NOT NULL,
    sections        JSONB           NOT NULL DEFAULT '[]',
    usage_count     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_scripts_user_id ON scripts(user_id);
CREATE INDEX idx_scripts_status ON scripts(status);
CREATE INDEX idx_script_sections_script_id ON script_sections(script_id);
