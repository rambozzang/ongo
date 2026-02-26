-- AI 콘텐츠 캘린더 제안
CREATE TABLE IF NOT EXISTS calendar_suggestions (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    suggested_date  DATE NOT NULL,
    suggested_time  VARCHAR(10) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    topic           VARCHAR(200),
    expected_engagement NUMERIC(5,2) DEFAULT 0,
    confidence      INT DEFAULT 0,
    status          VARCHAR(30) DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_calendar_suggestions_workspace ON calendar_suggestions(workspace_id);
CREATE INDEX idx_calendar_suggestions_status ON calendar_suggestions(workspace_id, status);

-- AI 최적 시간 슬롯
CREATE TABLE IF NOT EXISTS calendar_ai_slots (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    slot_date       DATE NOT NULL,
    slot_time       VARCHAR(10) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    score           INT DEFAULT 0,
    reason          TEXT,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_calendar_ai_slots_workspace ON calendar_ai_slots(workspace_id);
