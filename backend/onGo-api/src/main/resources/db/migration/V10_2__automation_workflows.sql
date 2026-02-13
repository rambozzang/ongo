-- Automation Workflows (multi-step with conditions)
CREATE TABLE automation_workflows (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,
    trigger_config JSONB DEFAULT '{}',
    enabled BOOLEAN DEFAULT FALSE,
    execution_count INT DEFAULT 0,
    last_executed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_automation_workflows_user_id ON automation_workflows(user_id);
CREATE INDEX idx_automation_workflows_enabled ON automation_workflows(enabled) WHERE enabled = TRUE;

-- Workflow Conditions (self-referencing for nested AND/OR groups)
CREATE TABLE workflow_conditions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES automation_workflows(id) ON DELETE CASCADE,
    parent_condition_id BIGINT REFERENCES workflow_conditions(id) ON DELETE CASCADE,
    group_type VARCHAR(10) DEFAULT 'AND',
    field VARCHAR(200),
    operator VARCHAR(30),
    value TEXT,
    expression TEXT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_workflow_conditions_workflow_id ON workflow_conditions(workflow_id);
CREATE INDEX idx_workflow_conditions_parent ON workflow_conditions(parent_condition_id);

-- Workflow Actions (ordered, with optional delay)
CREATE TABLE workflow_actions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES automation_workflows(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL,
    config JSONB DEFAULT '{}',
    delay_minutes INT DEFAULT 0,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_workflow_actions_workflow_id ON workflow_actions(workflow_id);

-- Workflow Execution History
CREATE TABLE workflow_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES automation_workflows(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    trigger_data JSONB DEFAULT '{}',
    status VARCHAR(20) DEFAULT 'RUNNING',
    action_results JSONB DEFAULT '[]',
    error_message TEXT,
    started_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);
CREATE INDEX idx_workflow_executions_workflow_id ON workflow_executions(workflow_id);
CREATE INDEX idx_workflow_executions_user_id ON workflow_executions(user_id);
