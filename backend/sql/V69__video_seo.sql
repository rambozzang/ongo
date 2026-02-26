-- 비디오 SEO 분석
CREATE TABLE IF NOT EXISTS seo_analyses (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    overall_score       INT DEFAULT 0,
    title_score         INT DEFAULT 0,
    description_score   INT DEFAULT 0,
    tag_score           INT DEFAULT 0,
    thumbnail_score     INT DEFAULT 0,
    suggestions         JSONB DEFAULT '[]',
    analyzed_at         TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_seo_analyses_workspace ON seo_analyses(workspace_id);
CREATE INDEX idx_seo_analyses_platform ON seo_analyses(platform);

-- SEO 키워드
CREATE TABLE IF NOT EXISTS seo_keywords (
    id                  BIGSERIAL PRIMARY KEY,
    analysis_id         BIGINT NOT NULL REFERENCES seo_analyses(id) ON DELETE CASCADE,
    keyword             VARCHAR(300) NOT NULL,
    search_volume       BIGINT DEFAULT 0,
    competition         VARCHAR(30) DEFAULT 'MEDIUM',
    relevance           INT DEFAULT 0,
    trend               VARCHAR(30) DEFAULT 'STABLE',
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_seo_keywords_analysis ON seo_keywords(analysis_id);
