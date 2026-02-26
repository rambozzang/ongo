-- 콘텐츠 클러스터
CREATE TABLE IF NOT EXISTS content_clusters (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    content_count   INT DEFAULT 0,
    avg_views       BIGINT DEFAULT 0,
    avg_engagement  NUMERIC(5,2) DEFAULT 0,
    top_content     VARCHAR(500),
    tags            JSONB DEFAULT '[]',
    platform        VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_content_clusters_workspace ON content_clusters(workspace_id);

-- 클러스터 내 콘텐츠
CREATE TABLE IF NOT EXISTS cluster_contents (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT NOT NULL REFERENCES content_clusters(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    views           BIGINT DEFAULT 0,
    likes           INT DEFAULT 0,
    engagement      NUMERIC(5,2) DEFAULT 0,
    published_at    TIMESTAMP,
    similarity      NUMERIC(4,3) DEFAULT 0,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_cluster_contents_cluster ON cluster_contents(cluster_id);
