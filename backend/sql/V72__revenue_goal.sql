-- 수익 목표
CREATE TABLE IF NOT EXISTS revenue_goals (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    name                VARCHAR(200) NOT NULL,
    target_amount       BIGINT NOT NULL,
    current_amount      BIGINT DEFAULT 0,
    currency            VARCHAR(10) DEFAULT 'KRW',
    period              VARCHAR(30) NOT NULL,
    platform            VARCHAR(50) DEFAULT 'ALL',
    status              VARCHAR(30) DEFAULT 'ACTIVE',
    progress            INT DEFAULT 0,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_revenue_goals_workspace ON revenue_goals(workspace_id);

-- 수익 목표 마일스톤
CREATE TABLE IF NOT EXISTS revenue_goal_milestones (
    id                  BIGSERIAL PRIMARY KEY,
    goal_id             BIGINT NOT NULL REFERENCES revenue_goals(id) ON DELETE CASCADE,
    label               VARCHAR(200) NOT NULL,
    target_amount       BIGINT NOT NULL,
    reached             BOOLEAN DEFAULT FALSE,
    reached_at          TIMESTAMP,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_revenue_goal_milestones_goal ON revenue_goal_milestones(goal_id);
