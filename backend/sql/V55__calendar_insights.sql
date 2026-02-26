-- Wave 15: 콘텐츠 캘린더 인사이트
CREATE TABLE calendar_insights (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    date            DATE NOT NULL,
    day_of_week     VARCHAR(10) NOT NULL,
    hour            INT NOT NULL,
    upload_count    INT NOT NULL DEFAULT 0,
    avg_views       BIGINT NOT NULL DEFAULT 0,
    avg_engagement  NUMERIC(5,2) NOT NULL DEFAULT 0,
    score           INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE optimal_time_slots (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    platform            VARCHAR(30),
    day_of_week         VARCHAR(10) NOT NULL,
    hour                INT NOT NULL,
    score               INT NOT NULL DEFAULT 0,
    expected_views      BIGINT NOT NULL DEFAULT 0,
    expected_engagement NUMERIC(5,2) NOT NULL DEFAULT 0,
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE upload_patterns (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    platform            VARCHAR(30) NOT NULL,
    total_uploads       INT NOT NULL DEFAULT 0,
    avg_uploads_per_week NUMERIC(5,2) NOT NULL DEFAULT 0,
    most_active_day     VARCHAR(10),
    most_active_hour    INT,
    consistency         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_calendar_insights_workspace ON calendar_insights(workspace_id);
CREATE INDEX idx_calendar_insights_date ON calendar_insights(workspace_id, date);
CREATE INDEX idx_optimal_time_slots_workspace ON optimal_time_slots(workspace_id);
CREATE INDEX idx_upload_patterns_workspace ON upload_patterns(workspace_id);
