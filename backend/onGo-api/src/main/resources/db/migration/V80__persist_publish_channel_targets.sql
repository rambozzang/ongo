-- Preserve the exact connected account used for every publication. This is
-- required when a creator reconnects a provider or has multiple integrations.
ALTER TABLE video_uploads
    ADD COLUMN IF NOT EXISTS channel_id BIGINT REFERENCES channels(id) ON DELETE SET NULL;

-- Existing rows are associated with the account that was active when the
-- upload was created. Rows for already-disconnected channels remain nullable
-- and continue through the legacy platform fallback path.
-- PostgreSQL does not allow the target UPDATE alias (vu) to be referenced from
-- a JOIN ... ON clause in the FROM list. Use a scalar subquery instead. Also
-- leave ambiguous legacy rows unassigned: before channel_id was persisted we
-- cannot know which of several same-provider accounts was used, and guessing
-- would make the historical publication point to the wrong account.
UPDATE video_uploads AS vu
SET channel_id = (
    SELECT MIN(c.id)
    FROM videos AS v
    JOIN channels AS c ON c.user_id = v.user_id
                       AND c.platform::text = vu.platform::text
    WHERE v.id = vu.video_id
    HAVING COUNT(*) = 1
)
WHERE vu.channel_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_video_uploads_channel_id ON video_uploads(channel_id);

-- The original schema allowed only one upload per provider.  That is not
-- sufficient once a creator connects two accounts on the same provider.
-- Keep one legacy row per provider for disconnected/old records (NULL
-- channel_id), while making connected-account rows unique by channel.
ALTER TABLE video_uploads
    DROP CONSTRAINT IF EXISTS uq_video_uploads_video_platform;
CREATE UNIQUE INDEX IF NOT EXISTS uq_video_uploads_video_platform_legacy
    ON video_uploads(video_id, platform)
    WHERE channel_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_video_uploads_video_channel
    ON video_uploads(video_id, channel_id)
    WHERE channel_id IS NOT NULL;

-- Postiz-style integrations allow more than one account for the same provider.
-- The exact account is selected by channel_id on each publication.
ALTER TABLE channels DROP CONSTRAINT IF EXISTS uq_channels_user_platform;
CREATE INDEX IF NOT EXISTS idx_channels_user_platform ON channels(user_id, platform);
