-- Preserve provider-specific Postiz settings for durable retries and scheduled dispatch.
ALTER TABLE video_platform_meta
    ADD COLUMN IF NOT EXISTS custom_settings_json TEXT;

COMMENT ON COLUMN video_platform_meta.custom_settings_json IS
    'Provider-specific Postiz settings retained for retries and scheduled publishing';
