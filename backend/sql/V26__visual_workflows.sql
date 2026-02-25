-- 비주얼 워크플로우
CREATE TABLE visual_workflows (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  name VARCHAR(100) NOT NULL,
  description TEXT,
  workflow_data JSONB NOT NULL,
  is_active BOOLEAN DEFAULT false,
  last_triggered_at TIMESTAMPTZ,
  trigger_count INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
