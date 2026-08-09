-- Recommendations must identify the connected account, not only the provider.
-- Existing rows remain valid as legacy provider-wide recommendations.
ALTER TABLE schedule_recommendations
    ADD COLUMN IF NOT EXISTS channel_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_schedule_recommendations_target
    ON schedule_recommendations (user_id, video_id, platform, channel_id, status);
