-- AI 캘린더
CREATE TABLE ai_content_calendars (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(200),
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  settings JSONB,
  calendar_data JSONB NOT NULL,
  status VARCHAR(20) DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ DEFAULT NOW()
);
