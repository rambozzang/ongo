-- AI 댓글 요약
CREATE TABLE IF NOT EXISTS comment_summary_results (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    total_comments      INT DEFAULT 0,
    positive_pct        INT DEFAULT 0,
    negative_pct        INT DEFAULT 0,
    neutral_pct         INT DEFAULT 0,
    top_topics          JSONB DEFAULT '[]',
    ai_summary          TEXT,
    analyzed_at         TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_comment_summary_workspace ON comment_summary_results(workspace_id);

-- 인기 댓글
CREATE TABLE IF NOT EXISTS top_comments (
    id                  BIGSERIAL PRIMARY KEY,
    summary_id          BIGINT NOT NULL REFERENCES comment_summary_results(id) ON DELETE CASCADE,
    author              VARCHAR(200) NOT NULL,
    text                TEXT NOT NULL,
    likes               INT DEFAULT 0,
    sentiment           VARCHAR(30) DEFAULT 'NEUTRAL',
    is_highlighted      BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_top_comments_summary ON top_comments(summary_id);
