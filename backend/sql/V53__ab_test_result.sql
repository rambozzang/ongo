-- Wave 14: A/B 테스트 결과
CREATE TABLE ab_test_results (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    test_id         BIGINT,
    test_name       VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    start_date      TIMESTAMPTZ NOT NULL,
    end_date        TIMESTAMPTZ,
    winner          VARCHAR(10),
    confidence      DECIMAL(5,2) NOT NULL DEFAULT 0,
    metric          VARCHAR(50) NOT NULL DEFAULT 'CTR',
    platform        VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE test_variants (
    id              BIGSERIAL PRIMARY KEY,
    result_id       BIGINT NOT NULL REFERENCES ab_test_results(id) ON DELETE CASCADE,
    variant_id      VARCHAR(10) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    thumbnail_url   TEXT,
    views           BIGINT NOT NULL DEFAULT 0,
    clicks          BIGINT NOT NULL DEFAULT 0,
    ctr             DECIMAL(5,2) NOT NULL DEFAULT 0,
    avg_watch_time  INT NOT NULL DEFAULT 0,
    engagement      DECIMAL(5,2) NOT NULL DEFAULT 0,
    conversions     INT NOT NULL DEFAULT 0,
    is_control      BOOLEAN NOT NULL DEFAULT false,
    is_winner       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ab_test_results_workspace ON ab_test_results(workspace_id);
CREATE INDEX idx_ab_test_results_status ON ab_test_results(workspace_id, status);
CREATE INDEX idx_test_variants_result ON test_variants(result_id);
