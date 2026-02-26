-- 크리에이터 벤치마크 결과
CREATE TABLE IF NOT EXISTS benchmark_results (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    category        VARCHAR(100) NOT NULL,
    my_value        NUMERIC(15,2) DEFAULT 0,
    avg_value       NUMERIC(15,2) DEFAULT 0,
    top_value       NUMERIC(15,2) DEFAULT 0,
    percentile      INT DEFAULT 0,
    metric          VARCHAR(100) NOT NULL,
    trend           VARCHAR(20) DEFAULT 'STABLE',
    updated_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_benchmark_results_workspace ON benchmark_results(workspace_id);
CREATE INDEX idx_benchmark_results_platform ON benchmark_results(workspace_id, platform);

-- 벤치마크 피어
CREATE TABLE IF NOT EXISTS benchmark_peers (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    subscribers     BIGINT DEFAULT 0,
    avg_views       BIGINT DEFAULT 0,
    engagement_rate NUMERIC(5,2) DEFAULT 0,
    category        VARCHAR(100),
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_benchmark_peers_workspace ON benchmark_peers(workspace_id);
