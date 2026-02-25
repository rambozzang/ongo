-- AI 성과 예측
CREATE TABLE performance_predictions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  video_id BIGINT REFERENCES videos(id),
  predicted_views BIGINT,
  predicted_likes BIGINT,
  predicted_engagement_rate DECIMAL(5,2),
  confidence_score DECIMAL(3,2),
  optimal_upload_time TIMESTAMPTZ,
  prediction_data JSONB,
  actual_views BIGINT,
  actual_likes BIGINT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
