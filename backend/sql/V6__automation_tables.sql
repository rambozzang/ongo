-- Automation Rules
CREATE TABLE automation_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,
    trigger_config JSONB DEFAULT '{}',
    action_type VARCHAR(50) NOT NULL,
    action_config JSONB DEFAULT '{}',
    is_active BOOLEAN DEFAULT FALSE,
    last_triggered_at TIMESTAMP,
    execution_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_automation_rules_user_id ON automation_rules(user_id);

-- Webhooks
CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    events TEXT[] DEFAULT '{}',
    secret VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    last_triggered_at TIMESTAMP,
    last_status_code INT,
    failure_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_webhooks_user_id ON webhooks(user_id);

-- Recurring Schedules
CREATE TABLE recurring_schedules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    day_of_week INT,
    day_of_month INT,
    time_of_day TIME NOT NULL,
    timezone VARCHAR(50) DEFAULT 'Asia/Seoul',
    platforms TEXT[] DEFAULT '{}',
    title_template VARCHAR(200),
    description_template TEXT,
    tags TEXT[] DEFAULT '{}',
    is_active BOOLEAN DEFAULT TRUE,
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_recurring_schedules_user_id ON recurring_schedules(user_id);
