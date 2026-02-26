-- 플랫폼 자동화
CREATE TABLE automation_rules (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL,
    action_type     VARCHAR(50) NOT NULL,
    condition_text  TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    execution_count INT NOT NULL DEFAULT 0,
    last_executed   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE automation_logs (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    rule_name       VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    message         TEXT,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auto_rules_user ON automation_rules(user_id);
CREATE INDEX idx_auto_rules_platform ON automation_rules(platform);
CREATE INDEX idx_auto_rules_active ON automation_rules(is_active);
CREATE INDEX idx_auto_logs_rule ON automation_logs(rule_id);
CREATE INDEX idx_auto_logs_status ON automation_logs(status);
