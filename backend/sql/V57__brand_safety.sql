-- Wave 15: 브랜드 안전성 점검
CREATE TABLE brand_safety_checks (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    content_title   VARCHAR(300) NOT NULL,
    content_type    VARCHAR(20) NOT NULL,
    platform        VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    overall_score   INT NOT NULL DEFAULT 0,
    checked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE safety_check_items (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL REFERENCES brand_safety_checks(id) ON DELETE CASCADE,
    category        VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'PASS',
    severity        VARCHAR(20) NOT NULL DEFAULT 'LOW',
    description     TEXT NOT NULL,
    recommendation  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE brand_safety_rules (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    description     TEXT NOT NULL,
    is_enabled      BOOLEAN NOT NULL DEFAULT true,
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_brand_safety_checks_workspace ON brand_safety_checks(workspace_id);
CREATE INDEX idx_brand_safety_checks_status ON brand_safety_checks(workspace_id, status);
CREATE INDEX idx_safety_check_items_check ON safety_check_items(check_id);
CREATE INDEX idx_brand_safety_rules_workspace ON brand_safety_rules(workspace_id);
