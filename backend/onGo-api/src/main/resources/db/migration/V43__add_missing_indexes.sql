-- videos: filter by source (GOOGLE_DRIVE vs UPLOAD_PC)
CREATE INDEX IF NOT EXISTS idx_videos_user_source_created ON videos(user_id, source, created_at DESC);

-- channels: global active channel scan
CREATE INDEX IF NOT EXISTS idx_channels_platform_status ON channels(platform, status);

-- comments: sentiment filtering
CREATE INDEX IF NOT EXISTS idx_comments_user_sentiment_created ON comments(user_id, sentiment, created_at DESC);

-- comments: video-specific comment lookup
CREATE INDEX IF NOT EXISTS idx_comments_user_video_created ON comments(user_id, video_id, created_at DESC);

-- drive_import_jobs: progress tracking by source
CREATE INDEX IF NOT EXISTS idx_drive_import_jobs_source_status ON drive_import_jobs(content_source_id, status);

-- user_content_sources: filter by status
CREATE INDEX IF NOT EXISTS idx_user_content_sources_user_type_status ON user_content_sources(user_id, source_type, status);

-- payments: status-based lookups
CREATE INDEX IF NOT EXISTS idx_payments_status_created ON payments(status, created_at DESC);

-- ai_credit_transactions: usage analytics by feature
CREATE INDEX IF NOT EXISTS idx_ai_credit_transactions_user_feature_created ON ai_credit_transactions(user_id, feature, created_at DESC);

-- revenue_alert_configs: enabled alerts lookup
CREATE INDEX IF NOT EXISTS idx_revenue_alert_configs_user_enabled ON revenue_alert_configs(user_id, is_enabled);

-- revenue_insights: type filtering
CREATE INDEX IF NOT EXISTS idx_revenue_insights_user_type_created ON revenue_insights(user_id, insight_type, created_at DESC);

-- webhook_events: recent unprocessed events
CREATE INDEX IF NOT EXISTS idx_webhook_events_status_created ON webhook_events(status, created_at DESC);

-- Add FKs where missing (separate blocks to avoid total rollback on partial constraint violation)
-- revenue_streams → channels
DO $$
BEGIN
    ALTER TABLE revenue_streams ADD CONSTRAINT fk_revenue_streams_channel FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'fk_revenue_streams_channel could not be added: %', SQLERRM;
END $$;

-- revenue_projections → channels
DO $$
BEGIN
    ALTER TABLE revenue_projections ADD CONSTRAINT fk_revenue_projections_channel FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'fk_revenue_projections_channel could not be added: %', SQLERRM;
END $$;

-- channel_health_metrics → channels
DO $$
BEGIN
    ALTER TABLE channel_health_metrics ADD CONSTRAINT fk_channel_health_metrics_channel FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'fk_channel_health_metrics_channel could not be added: %', SQLERRM;
END $$;

-- team_members → workspaces
DO $$
BEGIN
    ALTER TABLE team_members ADD CONSTRAINT fk_team_members_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'fk_team_members_workspace could not be added: %', SQLERRM;
END $$;

-- comments → videos
DO $$
BEGIN
    ALTER TABLE comments ADD CONSTRAINT fk_comments_video FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'fk_comments_video could not be added: %', SQLERRM;
END $$;
