CREATE TABLE ai_sentiment_daily_usage (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    batch_count INTEGER NOT NULL DEFAULT 0 CHECK (batch_count >= 0),
    PRIMARY KEY (user_id, usage_date)
);
