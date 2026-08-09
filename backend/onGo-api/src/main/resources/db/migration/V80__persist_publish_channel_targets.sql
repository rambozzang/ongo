-- Preserve the exact connected account used for every publication. This is
-- required when a creator reconnects a provider or has multiple integrations.
ALTER TABLE video_uploads
    ADD COLUMN IF NOT EXISTS channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL;

-- Existing rows are associated with the account that was active when the
-- upload was created. Rows for already-disconnected channels remain nullable
-- and continue through the legacy platform fallback path.
UPDATE video_uploads vu
SET channel_id = c.id
FROM videos v
JOIN channels c ON c.user_id = v.user_id AND c.platform::text = vu.platform::text
WHERE vu.video_id = v.id
  AND vu.channel_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_video_uploads_channel_id ON video_uploads(channel_id);

-- Postiz-style integrations allow more than one account for the same provider.
-- The exact account is selected by channel_id on each publication.
ALTER TABLE channels DROP CONSTRAINT IF EXISTS uq_channels_user_platform;
CREATE INDEX IF NOT EXISTS idx_channels_user_platform ON channels(user_id, platform);
