-- 썸네일 A/B 테스트
CREATE TABLE IF NOT EXISTS thumbnail_ab_tests (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    status              VARCHAR(30) DEFAULT 'PENDING',
    variant_a_image_url VARCHAR(1000),
    variant_a_ctr       NUMERIC(5,2) DEFAULT 0,
    variant_a_impressions BIGINT DEFAULT 0,
    variant_a_clicks    BIGINT DEFAULT 0,
    variant_b_image_url VARCHAR(1000),
    variant_b_ctr       NUMERIC(5,2) DEFAULT 0,
    variant_b_impressions BIGINT DEFAULT 0,
    variant_b_clicks    BIGINT DEFAULT 0,
    winner              VARCHAR(10),
    started_at          TIMESTAMP DEFAULT now(),
    ended_at            TIMESTAMP
);

CREATE INDEX idx_thumbnail_ab_tests_workspace ON thumbnail_ab_tests(workspace_id);
CREATE INDEX idx_thumbnail_ab_tests_status ON thumbnail_ab_tests(status);
