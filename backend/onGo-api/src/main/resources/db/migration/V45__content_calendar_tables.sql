-- AI 콘텐츠 캘린더 테이블
CREATE TABLE IF NOT EXISTS ai_content_calendars (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    settings TEXT,
    calendar_data TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_calendars_user_id ON ai_content_calendars(user_id);

-- 캘린더 AI 제안 테이블
CREATE TABLE IF NOT EXISTS calendar_suggestions (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    suggested_date DATE NOT NULL,
    suggested_time VARCHAR(10) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    topic VARCHAR(255),
    expected_engagement DECIMAL(10,2) DEFAULT 0,
    confidence INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_calendar_suggestions_workspace ON calendar_suggestions(workspace_id);
CREATE INDEX IF NOT EXISTS idx_calendar_suggestions_status ON calendar_suggestions(workspace_id, status);

-- 캘린더 최적 슬롯 테이블
CREATE TABLE IF NOT EXISTS calendar_ai_slots (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    slot_date DATE NOT NULL,
    slot_time VARCHAR(10) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    score INT DEFAULT 0,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_calendar_ai_slots_workspace_date ON calendar_ai_slots(workspace_id, slot_date);
