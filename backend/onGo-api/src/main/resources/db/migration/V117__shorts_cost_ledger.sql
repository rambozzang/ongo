CREATE TABLE ugc_shorts_cost_ledger (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('TRANSCRIPTION', 'LLM')),
    stage VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    audio_duration_seconds NUMERIC(12, 3),
    input_tokens BIGINT,
    output_tokens BIGINT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_shorts_cost_duration_nonnegative
        CHECK (audio_duration_seconds IS NULL OR audio_duration_seconds >= 0),
    CONSTRAINT ck_shorts_cost_input_tokens_nonnegative
        CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_shorts_cost_output_tokens_nonnegative
        CHECK (output_tokens IS NULL OR output_tokens >= 0)
);

CREATE INDEX idx_shorts_cost_ledger_run_id ON ugc_shorts_cost_ledger (run_id, id);
