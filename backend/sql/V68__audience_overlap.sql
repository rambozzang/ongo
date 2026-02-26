-- 오디언스 오버랩 분석 결과
CREATE TABLE IF NOT EXISTS audience_overlap_results (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    platform_a          VARCHAR(50) NOT NULL,
    platform_b          VARCHAR(50) NOT NULL,
    overlap_percent     NUMERIC(5,2) DEFAULT 0,
    unique_to_a         BIGINT DEFAULT 0,
    unique_to_b         BIGINT DEFAULT 0,
    shared_audience     BIGINT DEFAULT 0,
    analyzed_at         TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_audience_overlap_workspace ON audience_overlap_results(workspace_id);

-- 오버랩 세그먼트
CREATE TABLE IF NOT EXISTS overlap_segments (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    name                VARCHAR(200) NOT NULL,
    platforms           JSONB DEFAULT '[]',
    audience_size       BIGINT DEFAULT 0,
    engagement_rate     NUMERIC(5,2) DEFAULT 0,
    top_interest        VARCHAR(200),
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_overlap_segments_workspace ON overlap_segments(workspace_id);
