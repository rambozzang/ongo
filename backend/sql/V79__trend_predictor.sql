-- AI 트렌드 예측기
CREATE TABLE trend_predictions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    keyword         VARCHAR(200) NOT NULL,
    category        VARCHAR(100) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    current_score   INT NOT NULL DEFAULT 0,
    predicted_score INT NOT NULL DEFAULT 0,
    confidence      NUMERIC(5,2) NOT NULL DEFAULT 0,
    direction       VARCHAR(20) NOT NULL DEFAULT 'STABLE',
    peak_date       DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE trend_topics (
    id              BIGSERIAL PRIMARY KEY,
    prediction_id   BIGINT NOT NULL REFERENCES trend_predictions(id) ON DELETE CASCADE,
    topic           VARCHAR(200) NOT NULL,
    related_keywords JSONB NOT NULL DEFAULT '[]',
    video_count     INT NOT NULL DEFAULT 0,
    avg_views       BIGINT NOT NULL DEFAULT 0,
    growth_rate     NUMERIC(7,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_trend_predictions_user ON trend_predictions(user_id);
CREATE INDEX idx_trend_predictions_category ON trend_predictions(category);
CREATE INDEX idx_trend_predictions_direction ON trend_predictions(direction);
CREATE INDEX idx_trend_topics_prediction ON trend_topics(prediction_id);
