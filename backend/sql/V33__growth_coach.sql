-- 크리에이터 성장 코치
CREATE TABLE IF NOT EXISTS growth_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    target_value BIGINT NOT NULL,
    current_value BIGINT DEFAULT 0,
    deadline VARCHAR(20),
    progress INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_growth_goals_user ON growth_goals(user_id);

CREATE TABLE IF NOT EXISTS weekly_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    week_start VARCHAR(20) NOT NULL,
    week_end VARCHAR(20) NOT NULL,
    summary TEXT,
    highlights JSONB DEFAULT '[]',
    concerns JSONB DEFAULT '[]',
    subscriber_growth INT DEFAULT 0,
    views_growth INT DEFAULT 0,
    engagement_change DECIMAL(5,2) DEFAULT 0,
    top_content JSONB DEFAULT '[]',
    action_items JSONB DEFAULT '[]',
    overall_score INT DEFAULT 0,
    generated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_weekly_reports_user ON weekly_reports(user_id);
CREATE INDEX idx_weekly_reports_week ON weekly_reports(week_start);

CREATE TABLE IF NOT EXISTS growth_insights (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    description TEXT,
    impact VARCHAR(20),
    category VARCHAR(100),
    actionable BOOLEAN DEFAULT FALSE,
    suggested_action TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_growth_insights_user ON growth_insights(user_id);
