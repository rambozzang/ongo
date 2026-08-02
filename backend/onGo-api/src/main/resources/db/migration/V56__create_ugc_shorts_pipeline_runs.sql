CREATE TABLE ugc_shorts_pipeline_runs (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    source_video_id BIGINT NOT NULL,
    template_id     BIGINT,                      -- ugc_shorts_templates.id (NULL 이면 기본 템플릿)
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    current_stage   VARCHAR(30),
    transcript_text TEXT,
    clip_count      INT NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ugc_shorts_runs_workspace ON ugc_shorts_pipeline_runs(workspace_id, created_at DESC);
CREATE INDEX idx_ugc_shorts_runs_user ON ugc_shorts_pipeline_runs(user_id, status);

CREATE TABLE ugc_shorts_run_stages (
    id              BIGSERIAL PRIMARY KEY,
    run_id          BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    stage           VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    prompt_id       BIGINT,       -- 실행에 쓴 프롬프트 (추적용 스냅샷)
    prompt_revision INT,
    ai_provider     VARCHAR(20),
    credit_cost     INT NOT NULL DEFAULT 0,
    input_snapshot  JSONB,
    output_snapshot JSONB,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_run_stages UNIQUE (run_id, stage)
);
CREATE INDEX idx_ugc_shorts_run_stages_run ON ugc_shorts_run_stages(run_id);

CREATE TABLE ugc_shorts_clips (
    id                BIGSERIAL PRIMARY KEY,
    run_id            BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    seq               INT NOT NULL,
    start_ms          BIGINT NOT NULL,
    end_ms            BIGINT NOT NULL,
    title             VARCHAR(300),
    caption           TEXT,
    subtitle_json     JSONB,
    crop_json         JSONB,
    render_spec       JSONB,
    status            VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    dedup_key         VARCHAR(120),
    rendered_video_id BIGINT,
    scheduled_at      TIMESTAMP,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_clips_seq UNIQUE (run_id, seq),
    CONSTRAINT chk_ugc_shorts_clips_range CHECK (end_ms > start_ms)
);
CREATE UNIQUE INDEX uq_ugc_shorts_clips_dedup
    ON ugc_shorts_clips(dedup_key) WHERE dedup_key IS NOT NULL;

CREATE TABLE ugc_shorts_clip_hooks (
    id         BIGSERIAL PRIMARY KEY,
    clip_id    BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    variant    VARCHAR(10) NOT NULL,     -- A / B / CUSTOM
    text       VARCHAR(300) NOT NULL,
    selected   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_clip_hooks UNIQUE (clip_id, variant)
);
CREATE INDEX idx_ugc_shorts_clip_hooks_clip ON ugc_shorts_clip_hooks(clip_id);
