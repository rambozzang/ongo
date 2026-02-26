-- Wave 14: 수익 분배 관리
CREATE TABLE revenue_splits (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    total_amount    BIGINT NOT NULL DEFAULT 0,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    period          VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE split_members (
    id              BIGSERIAL PRIMARY KEY,
    split_id        BIGINT NOT NULL REFERENCES revenue_splits(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(200),
    role            VARCHAR(50),
    percentage      DECIMAL(5,2) NOT NULL,
    amount          BIGINT NOT NULL DEFAULT 0,
    payment_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_revenue_splits_workspace ON revenue_splits(workspace_id);
CREATE INDEX idx_revenue_splits_status ON revenue_splits(workspace_id, status);
CREATE INDEX idx_split_members_split ON split_members(split_id);
