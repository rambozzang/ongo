-- 콘텐츠 리퍼포징 작업
CREATE TABLE IF NOT EXISTS repurpose_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    original_title      VARCHAR(500) NOT NULL,
    original_platform   VARCHAR(50) NOT NULL,
    target_platform     VARCHAR(50) NOT NULL,
    target_format       VARCHAR(50) NOT NULL,
    status              VARCHAR(30) DEFAULT 'PENDING',
    progress            INT DEFAULT 0,
    output_url          VARCHAR(1000),
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_repurpose_jobs_workspace ON repurpose_jobs(workspace_id);
CREATE INDEX idx_repurpose_jobs_status ON repurpose_jobs(status);

-- 리퍼포징 템플릿
CREATE TABLE IF NOT EXISTS repurpose_templates (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT,
    name                VARCHAR(200) NOT NULL,
    source_platform     VARCHAR(50) NOT NULL,
    target_platform     VARCHAR(50) NOT NULL,
    target_format       VARCHAR(50) NOT NULL,
    description         TEXT,
    is_default          BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_repurpose_templates_workspace ON repurpose_templates(workspace_id);
