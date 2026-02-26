-- Wave 16: AI 감정 분석기
CREATE TABLE sentiment_results (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    content_title       VARCHAR(300) NOT NULL,
    platform            VARCHAR(30) NOT NULL,
    total_comments      INT NOT NULL DEFAULT 0,
    positive_count      INT NOT NULL DEFAULT 0,
    neutral_count       INT NOT NULL DEFAULT 0,
    negative_count      INT NOT NULL DEFAULT 0,
    positive_rate       NUMERIC(5,2) NOT NULL DEFAULT 0,
    avg_sentiment_score INT NOT NULL DEFAULT 0,
    top_positive_keywords JSONB NOT NULL DEFAULT '[]',
    top_negative_keywords JSONB NOT NULL DEFAULT '[]',
    analyzed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE comment_sentiments (
    id              BIGSERIAL PRIMARY KEY,
    result_id       BIGINT NOT NULL REFERENCES sentiment_results(id) ON DELETE CASCADE,
    comment_text    TEXT NOT NULL,
    author_name     VARCHAR(100) NOT NULL,
    sentiment       VARCHAR(10) NOT NULL DEFAULT 'NEUTRAL',
    score           INT NOT NULL DEFAULT 50,
    keywords        JSONB NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sentiment_results_workspace ON sentiment_results(workspace_id);
CREATE INDEX idx_comment_sentiments_result ON comment_sentiments(result_id);
CREATE INDEX idx_comment_sentiments_sentiment ON comment_sentiments(result_id, sentiment);
