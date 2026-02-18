--
-- PostgreSQL database dump
--

\restrict Wmy1STn2saAp7mgXuY8fWCJlHvMWKLwianurcqNUIuRol2oyzQUh1rikENTE0p6

-- Dumped from database version 16.11 (Debian 16.11-1.pgdg13+1)
-- Dumped by pg_dump version 16.11 (Debian 16.11-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.workflow_executions DROP CONSTRAINT IF EXISTS workflow_executions_workflow_id_fkey;
ALTER TABLE IF EXISTS ONLY public.workflow_executions DROP CONSTRAINT IF EXISTS workflow_executions_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.workflow_conditions DROP CONSTRAINT IF EXISTS workflow_conditions_workflow_id_fkey;
ALTER TABLE IF EXISTS ONLY public.workflow_conditions DROP CONSTRAINT IF EXISTS workflow_conditions_parent_condition_id_fkey;
ALTER TABLE IF EXISTS ONLY public.workflow_actions DROP CONSTRAINT IF EXISTS workflow_actions_workflow_id_fkey;
ALTER TABLE IF EXISTS ONLY public.weekly_digests DROP CONSTRAINT IF EXISTS weekly_digests_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.webhooks DROP CONSTRAINT IF EXISTS webhooks_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.watermarks DROP CONSTRAINT IF EXISTS watermarks_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.videos DROP CONSTRAINT IF EXISTS videos_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.video_uploads DROP CONSTRAINT IF EXISTS video_uploads_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.video_subtitles DROP CONSTRAINT IF EXISTS video_subtitles_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.video_processing_progress DROP CONSTRAINT IF EXISTS video_processing_progress_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.video_platform_meta DROP CONSTRAINT IF EXISTS video_platform_meta_video_upload_id_fkey;
ALTER TABLE IF EXISTS ONLY public.video_media_info DROP CONSTRAINT IF EXISTS video_media_info_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.user_settings DROP CONSTRAINT IF EXISTS user_settings_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.templates DROP CONSTRAINT IF EXISTS templates_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.team_members DROP CONSTRAINT IF EXISTS team_members_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.subscriptions DROP CONSTRAINT IF EXISTS subscriptions_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.schedules DROP CONSTRAINT IF EXISTS schedules_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.schedules DROP CONSTRAINT IF EXISTS schedules_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.recycling_suggestions DROP CONSTRAINT IF EXISTS recycling_suggestions_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.recycling_suggestions DROP CONSTRAINT IF EXISTS recycling_suggestions_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.recurring_schedules DROP CONSTRAINT IF EXISTS recurring_schedules_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.payments DROP CONSTRAINT IF EXISTS payments_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.notifications DROP CONSTRAINT IF EXISTS notifications_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.link_bio_pages DROP CONSTRAINT IF EXISTS link_bio_pages_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.link_bio_links DROP CONSTRAINT IF EXISTS link_bio_links_page_id_fkey;
ALTER TABLE IF EXISTS ONLY public.inbox_messages DROP CONSTRAINT IF EXISTS inbox_messages_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.inbox_messages DROP CONSTRAINT IF EXISTS inbox_messages_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ideas DROP CONSTRAINT IF EXISTS ideas_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.goals DROP CONSTRAINT IF EXISTS goals_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.goal_milestones DROP CONSTRAINT IF EXISTS goal_milestones_goal_id_fkey;
ALTER TABLE IF EXISTS ONLY public.content_images DROP CONSTRAINT IF EXISTS content_images_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.competitors DROP CONSTRAINT IF EXISTS competitors_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_parent_comment_id_fkey;
ALTER TABLE IF EXISTS ONLY public.channels DROP CONSTRAINT IF EXISTS channels_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.brand_kits DROP CONSTRAINT IF EXISTS brand_kits_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.automation_workflows DROP CONSTRAINT IF EXISTS automation_workflows_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.automation_rules DROP CONSTRAINT IF EXISTS automation_rules_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.assets DROP CONSTRAINT IF EXISTS assets_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approvals DROP CONSTRAINT IF EXISTS approvals_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approvals DROP CONSTRAINT IF EXISTS approvals_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approvals DROP CONSTRAINT IF EXISTS approvals_reviewer_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approvals DROP CONSTRAINT IF EXISTS approvals_requester_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approval_comments DROP CONSTRAINT IF EXISTS approval_comments_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approval_comments DROP CONSTRAINT IF EXISTS approval_comments_approval_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approval_chains DROP CONSTRAINT IF EXISTS approval_chains_approver_id_fkey;
ALTER TABLE IF EXISTS ONLY public.approval_chains DROP CONSTRAINT IF EXISTS approval_chains_approval_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ai_purchased_credits DROP CONSTRAINT IF EXISTS ai_purchased_credits_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ai_credits DROP CONSTRAINT IF EXISTS ai_credits_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ai_credit_transactions DROP CONSTRAINT IF EXISTS ai_credit_transactions_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.activity_logs DROP CONSTRAINT IF EXISTS activity_logs_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ab_tests DROP CONSTRAINT IF EXISTS ab_tests_video_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ab_tests DROP CONSTRAINT IF EXISTS ab_tests_user_id_fkey;
ALTER TABLE IF EXISTS ONLY public.ab_test_variants DROP CONSTRAINT IF EXISTS ab_test_variants_test_id_fkey;
DROP TRIGGER IF EXISTS trg_weekly_digests_updated_at ON public.weekly_digests;
DROP TRIGGER IF EXISTS trg_videos_updated_at ON public.videos;
DROP TRIGGER IF EXISTS trg_video_uploads_updated_at ON public.video_uploads;
DROP TRIGGER IF EXISTS trg_video_platform_meta_updated_at ON public.video_platform_meta;
DROP TRIGGER IF EXISTS trg_users_updated_at ON public.users;
DROP TRIGGER IF EXISTS trg_user_settings_updated_at ON public.user_settings;
DROP TRIGGER IF EXISTS trg_subscriptions_updated_at ON public.subscriptions;
DROP TRIGGER IF EXISTS trg_schedules_updated_at ON public.schedules;
DROP TRIGGER IF EXISTS trg_channels_updated_at ON public.channels;
DROP TRIGGER IF EXISTS trg_approvals_updated_at ON public.approvals;
DROP TRIGGER IF EXISTS trg_approval_chains_updated_at ON public.approval_chains;
DROP TRIGGER IF EXISTS trg_ai_credits_updated_at ON public.ai_credits;
DROP INDEX IF EXISTS public.idx_workflow_executions_workflow_id;
DROP INDEX IF EXISTS public.idx_workflow_executions_user_id;
DROP INDEX IF EXISTS public.idx_workflow_conditions_workflow_id;
DROP INDEX IF EXISTS public.idx_workflow_conditions_parent;
DROP INDEX IF EXISTS public.idx_workflow_actions_workflow_id;
DROP INDEX IF EXISTS public.idx_weekly_digests_user_week;
DROP INDEX IF EXISTS public.idx_weekly_digests_user_id;
DROP INDEX IF EXISTS public.idx_webhooks_user_id;
DROP INDEX IF EXISTS public.idx_watermarks_user_id;
DROP INDEX IF EXISTS public.idx_videos_user_created;
DROP INDEX IF EXISTS public.idx_videos_user_content_hash;
DROP INDEX IF EXISTS public.idx_videos_status;
DROP INDEX IF EXISTS public.idx_videos_media_type;
DROP INDEX IF EXISTS public.idx_video_uploads_video_id;
DROP INDEX IF EXISTS public.idx_video_uploads_platform_video;
DROP INDEX IF EXISTS public.idx_video_uploads_active;
DROP INDEX IF EXISTS public.idx_video_subtitles_video_id;
DROP INDEX IF EXISTS public.idx_video_processing_progress_video_id;
DROP INDEX IF EXISTS public.idx_video_platform_meta_upload;
DROP INDEX IF EXISTS public.idx_video_media_info_video_id;
DROP INDEX IF EXISTS public.idx_users_role;
DROP INDEX IF EXISTS public.idx_templates_user_id;
DROP INDEX IF EXISTS public.idx_team_members_user_id;
DROP INDEX IF EXISTS public.idx_team_members_user_email;
DROP INDEX IF EXISTS public.idx_subscriptions_next_billing;
DROP INDEX IF EXISTS public.idx_schedules_video;
DROP INDEX IF EXISTS public.idx_schedules_user;
DROP INDEX IF EXISTS public.idx_schedules_pending;
DROP INDEX IF EXISTS public.idx_refresh_tokens_user;
DROP INDEX IF EXISTS public.idx_refresh_tokens_expires;
DROP INDEX IF EXISTS public.idx_recycling_suggestions_user_id;
DROP INDEX IF EXISTS public.idx_recycling_suggestions_status;
DROP INDEX IF EXISTS public.idx_recurring_schedules_user_id;
DROP INDEX IF EXISTS public.idx_purchased_user_active;
DROP INDEX IF EXISTS public.idx_payments_user_date;
DROP INDEX IF EXISTS public.idx_payments_pg_tx;
DROP INDEX IF EXISTS public.idx_notifications_user_unread;
DROP INDEX IF EXISTS public.idx_notifications_user_type;
DROP INDEX IF EXISTS public.idx_link_bio_pages_user_id;
DROP INDEX IF EXISTS public.idx_link_bio_pages_slug;
DROP INDEX IF EXISTS public.idx_link_bio_links_page_id;
DROP INDEX IF EXISTS public.idx_inbox_messages_user_id;
DROP INDEX IF EXISTS public.idx_inbox_messages_is_read;
DROP INDEX IF EXISTS public.idx_ideas_user_id;
DROP INDEX IF EXISTS public.idx_ideas_status;
DROP INDEX IF EXISTS public.idx_goals_user_id;
DROP INDEX IF EXISTS public.idx_goal_milestones_goal_id;
DROP INDEX IF EXISTS public.idx_credit_tx_user_date;
DROP INDEX IF EXISTS public.idx_content_images_video_id;
DROP INDEX IF EXISTS public.idx_competitors_user_id;
DROP INDEX IF EXISTS public.idx_comments_video_id;
DROP INDEX IF EXISTS public.idx_comments_user_id;
DROP INDEX IF EXISTS public.idx_comments_synced_at;
DROP INDEX IF EXISTS public.idx_comments_platform_video;
DROP INDEX IF EXISTS public.idx_comments_platform_dedup;
DROP INDEX IF EXISTS public.idx_comments_parent;
DROP INDEX IF EXISTS public.idx_channels_status;
DROP INDEX IF EXISTS public.idx_brand_kits_user_id;
DROP INDEX IF EXISTS public.idx_automation_workflows_user_id;
DROP INDEX IF EXISTS public.idx_automation_workflows_enabled;
DROP INDEX IF EXISTS public.idx_automation_rules_user_id;
DROP INDEX IF EXISTS public.idx_assets_user_id;
DROP INDEX IF EXISTS public.idx_approvals_user_id;
DROP INDEX IF EXISTS public.idx_approvals_status;
DROP INDEX IF EXISTS public.idx_approvals_reviewer_id;
DROP INDEX IF EXISTS public.idx_approval_comments_approval_id;
DROP INDEX IF EXISTS public.idx_approval_chains_deadline;
DROP INDEX IF EXISTS public.idx_approval_chains_approver_id;
DROP INDEX IF EXISTS public.idx_approval_chains_approval_id;
DROP INDEX IF EXISTS public.idx_activity_logs_user_id;
DROP INDEX IF EXISTS public.idx_activity_logs_created_at;
DROP INDEX IF EXISTS public.idx_ab_tests_user_id;
DROP INDEX IF EXISTS public.idx_ab_test_variants_test_id;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.workflow_executions DROP CONSTRAINT IF EXISTS workflow_executions_pkey;
ALTER TABLE IF EXISTS ONLY public.workflow_conditions DROP CONSTRAINT IF EXISTS workflow_conditions_pkey;
ALTER TABLE IF EXISTS ONLY public.workflow_actions DROP CONSTRAINT IF EXISTS workflow_actions_pkey;
ALTER TABLE IF EXISTS ONLY public.weekly_digests DROP CONSTRAINT IF EXISTS weekly_digests_pkey;
ALTER TABLE IF EXISTS ONLY public.webhooks DROP CONSTRAINT IF EXISTS webhooks_pkey;
ALTER TABLE IF EXISTS ONLY public.watermarks DROP CONSTRAINT IF EXISTS watermarks_pkey;
ALTER TABLE IF EXISTS ONLY public.videos DROP CONSTRAINT IF EXISTS videos_pkey;
ALTER TABLE IF EXISTS ONLY public.video_uploads DROP CONSTRAINT IF EXISTS video_uploads_pkey;
ALTER TABLE IF EXISTS ONLY public.video_subtitles DROP CONSTRAINT IF EXISTS video_subtitles_video_id_language_key;
ALTER TABLE IF EXISTS ONLY public.video_subtitles DROP CONSTRAINT IF EXISTS video_subtitles_pkey;
ALTER TABLE IF EXISTS ONLY public.video_processing_progress DROP CONSTRAINT IF EXISTS video_processing_progress_video_id_stage_platform_key;
ALTER TABLE IF EXISTS ONLY public.video_processing_progress DROP CONSTRAINT IF EXISTS video_processing_progress_pkey;
ALTER TABLE IF EXISTS ONLY public.video_platform_meta DROP CONSTRAINT IF EXISTS video_platform_meta_pkey;
ALTER TABLE IF EXISTS ONLY public.video_media_info DROP CONSTRAINT IF EXISTS video_media_info_video_id_key;
ALTER TABLE IF EXISTS ONLY public.video_media_info DROP CONSTRAINT IF EXISTS video_media_info_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE IF EXISTS ONLY public.user_settings DROP CONSTRAINT IF EXISTS user_settings_user_id_key;
ALTER TABLE IF EXISTS ONLY public.user_settings DROP CONSTRAINT IF EXISTS user_settings_pkey;
ALTER TABLE IF EXISTS ONLY public.video_uploads DROP CONSTRAINT IF EXISTS uq_video_uploads_video_platform;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS uq_users_provider;
ALTER TABLE IF EXISTS ONLY public.channels DROP CONSTRAINT IF EXISTS uq_channels_user_platform;
ALTER TABLE IF EXISTS ONLY public.templates DROP CONSTRAINT IF EXISTS templates_pkey;
ALTER TABLE IF EXISTS ONLY public.team_members DROP CONSTRAINT IF EXISTS team_members_pkey;
ALTER TABLE IF EXISTS ONLY public.subscriptions DROP CONSTRAINT IF EXISTS subscriptions_user_id_key;
ALTER TABLE IF EXISTS ONLY public.subscriptions DROP CONSTRAINT IF EXISTS subscriptions_pkey;
ALTER TABLE IF EXISTS ONLY public.schedules DROP CONSTRAINT IF EXISTS schedules_pkey;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_token_key;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_pkey;
ALTER TABLE IF EXISTS ONLY public.recycling_suggestions DROP CONSTRAINT IF EXISTS recycling_suggestions_pkey;
ALTER TABLE IF EXISTS ONLY public.recurring_schedules DROP CONSTRAINT IF EXISTS recurring_schedules_pkey;
ALTER TABLE IF EXISTS ONLY public.payments DROP CONSTRAINT IF EXISTS payments_pkey;
ALTER TABLE IF EXISTS ONLY public.notifications DROP CONSTRAINT IF EXISTS notifications_pkey;
ALTER TABLE IF EXISTS ONLY public.link_bio_pages DROP CONSTRAINT IF EXISTS link_bio_pages_slug_key;
ALTER TABLE IF EXISTS ONLY public.link_bio_pages DROP CONSTRAINT IF EXISTS link_bio_pages_pkey;
ALTER TABLE IF EXISTS ONLY public.link_bio_links DROP CONSTRAINT IF EXISTS link_bio_links_pkey;
ALTER TABLE IF EXISTS ONLY public.inbox_messages DROP CONSTRAINT IF EXISTS inbox_messages_pkey;
ALTER TABLE IF EXISTS ONLY public.ideas DROP CONSTRAINT IF EXISTS ideas_pkey;
ALTER TABLE IF EXISTS ONLY public.goals DROP CONSTRAINT IF EXISTS goals_pkey;
ALTER TABLE IF EXISTS ONLY public.goal_milestones DROP CONSTRAINT IF EXISTS goal_milestones_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.content_images DROP CONSTRAINT IF EXISTS content_images_pkey;
ALTER TABLE IF EXISTS ONLY public.competitors DROP CONSTRAINT IF EXISTS competitors_pkey;
ALTER TABLE IF EXISTS ONLY public.comments DROP CONSTRAINT IF EXISTS comments_pkey;
ALTER TABLE IF EXISTS ONLY public.channels DROP CONSTRAINT IF EXISTS channels_pkey;
ALTER TABLE IF EXISTS ONLY public.brand_kits DROP CONSTRAINT IF EXISTS brand_kits_pkey;
ALTER TABLE IF EXISTS ONLY public.automation_workflows DROP CONSTRAINT IF EXISTS automation_workflows_pkey;
ALTER TABLE IF EXISTS ONLY public.automation_rules DROP CONSTRAINT IF EXISTS automation_rules_pkey;
ALTER TABLE IF EXISTS ONLY public.assets DROP CONSTRAINT IF EXISTS assets_pkey;
ALTER TABLE IF EXISTS ONLY public.approvals DROP CONSTRAINT IF EXISTS approvals_pkey;
ALTER TABLE IF EXISTS ONLY public.approval_comments DROP CONSTRAINT IF EXISTS approval_comments_pkey;
ALTER TABLE IF EXISTS ONLY public.approval_chains DROP CONSTRAINT IF EXISTS approval_chains_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_12 DROP CONSTRAINT IF EXISTS analytics_daily_2026_12_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_12 DROP CONSTRAINT IF EXISTS analytics_daily_2026_12_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_11 DROP CONSTRAINT IF EXISTS analytics_daily_2026_11_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_11 DROP CONSTRAINT IF EXISTS analytics_daily_2026_11_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_10 DROP CONSTRAINT IF EXISTS analytics_daily_2026_10_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_10 DROP CONSTRAINT IF EXISTS analytics_daily_2026_10_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_09 DROP CONSTRAINT IF EXISTS analytics_daily_2026_09_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_09 DROP CONSTRAINT IF EXISTS analytics_daily_2026_09_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_08 DROP CONSTRAINT IF EXISTS analytics_daily_2026_08_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_08 DROP CONSTRAINT IF EXISTS analytics_daily_2026_08_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_07 DROP CONSTRAINT IF EXISTS analytics_daily_2026_07_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_07 DROP CONSTRAINT IF EXISTS analytics_daily_2026_07_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_06 DROP CONSTRAINT IF EXISTS analytics_daily_2026_06_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_06 DROP CONSTRAINT IF EXISTS analytics_daily_2026_06_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_05 DROP CONSTRAINT IF EXISTS analytics_daily_2026_05_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_05 DROP CONSTRAINT IF EXISTS analytics_daily_2026_05_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_04 DROP CONSTRAINT IF EXISTS analytics_daily_2026_04_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_04 DROP CONSTRAINT IF EXISTS analytics_daily_2026_04_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_03 DROP CONSTRAINT IF EXISTS analytics_daily_2026_03_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_03 DROP CONSTRAINT IF EXISTS analytics_daily_2026_03_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_02 DROP CONSTRAINT IF EXISTS analytics_daily_2026_02_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_02 DROP CONSTRAINT IF EXISTS analytics_daily_2026_02_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_01 DROP CONSTRAINT IF EXISTS analytics_daily_2026_01_video_upload_id_date_key;
ALTER TABLE IF EXISTS ONLY public.analytics_daily DROP CONSTRAINT IF EXISTS uq_analytics_upload_date;
ALTER TABLE IF EXISTS ONLY public.analytics_daily_2026_01 DROP CONSTRAINT IF EXISTS analytics_daily_2026_01_pkey;
ALTER TABLE IF EXISTS ONLY public.analytics_daily DROP CONSTRAINT IF EXISTS analytics_daily_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_purchased_credits DROP CONSTRAINT IF EXISTS ai_purchased_credits_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_credits DROP CONSTRAINT IF EXISTS ai_credits_user_id_key;
ALTER TABLE IF EXISTS ONLY public.ai_credits DROP CONSTRAINT IF EXISTS ai_credits_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_credit_transactions DROP CONSTRAINT IF EXISTS ai_credit_transactions_pkey;
ALTER TABLE IF EXISTS ONLY public.activity_logs DROP CONSTRAINT IF EXISTS activity_logs_pkey;
ALTER TABLE IF EXISTS ONLY public.ab_tests DROP CONSTRAINT IF EXISTS ab_tests_pkey;
ALTER TABLE IF EXISTS ONLY public.ab_test_variants DROP CONSTRAINT IF EXISTS ab_test_variants_pkey;
ALTER TABLE IF EXISTS public.workflow_executions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.workflow_conditions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.workflow_actions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.weekly_digests ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.webhooks ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.watermarks ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.videos ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.video_uploads ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.video_subtitles ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.video_processing_progress ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.video_platform_meta ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.video_media_info ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.users ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.user_settings ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.templates ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.team_members ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.subscriptions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.schedules ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.refresh_tokens ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.recycling_suggestions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.recurring_schedules ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.payments ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.notifications ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.link_bio_pages ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.link_bio_links ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.inbox_messages ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ideas ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.goals ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.goal_milestones ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.content_images ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.competitors ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.comments ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.channels ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.brand_kits ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.automation_workflows ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.automation_rules ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.assets ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.approvals ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.approval_comments ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.approval_chains ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.analytics_daily ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ai_purchased_credits ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ai_credits ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ai_credit_transactions ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.activity_logs ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ab_tests ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ab_test_variants ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.workflow_executions_id_seq;
DROP TABLE IF EXISTS public.workflow_executions;
DROP SEQUENCE IF EXISTS public.workflow_conditions_id_seq;
DROP TABLE IF EXISTS public.workflow_conditions;
DROP SEQUENCE IF EXISTS public.workflow_actions_id_seq;
DROP TABLE IF EXISTS public.workflow_actions;
DROP SEQUENCE IF EXISTS public.weekly_digests_id_seq;
DROP TABLE IF EXISTS public.weekly_digests;
DROP SEQUENCE IF EXISTS public.webhooks_id_seq;
DROP TABLE IF EXISTS public.webhooks;
DROP SEQUENCE IF EXISTS public.watermarks_id_seq;
DROP TABLE IF EXISTS public.watermarks;
DROP SEQUENCE IF EXISTS public.videos_id_seq;
DROP TABLE IF EXISTS public.videos;
DROP SEQUENCE IF EXISTS public.video_uploads_id_seq;
DROP TABLE IF EXISTS public.video_uploads;
DROP SEQUENCE IF EXISTS public.video_subtitles_id_seq;
DROP TABLE IF EXISTS public.video_subtitles;
DROP SEQUENCE IF EXISTS public.video_processing_progress_id_seq;
DROP TABLE IF EXISTS public.video_processing_progress;
DROP SEQUENCE IF EXISTS public.video_platform_meta_id_seq;
DROP TABLE IF EXISTS public.video_platform_meta;
DROP SEQUENCE IF EXISTS public.video_media_info_id_seq;
DROP TABLE IF EXISTS public.video_media_info;
DROP SEQUENCE IF EXISTS public.users_id_seq;
DROP TABLE IF EXISTS public.users;
DROP SEQUENCE IF EXISTS public.user_settings_id_seq;
DROP TABLE IF EXISTS public.user_settings;
DROP SEQUENCE IF EXISTS public.templates_id_seq;
DROP TABLE IF EXISTS public.templates;
DROP SEQUENCE IF EXISTS public.team_members_id_seq;
DROP TABLE IF EXISTS public.team_members;
DROP SEQUENCE IF EXISTS public.subscriptions_id_seq;
DROP TABLE IF EXISTS public.subscriptions;
DROP SEQUENCE IF EXISTS public.schedules_id_seq;
DROP TABLE IF EXISTS public.schedules;
DROP SEQUENCE IF EXISTS public.refresh_tokens_id_seq;
DROP TABLE IF EXISTS public.refresh_tokens;
DROP SEQUENCE IF EXISTS public.recycling_suggestions_id_seq;
DROP TABLE IF EXISTS public.recycling_suggestions;
DROP SEQUENCE IF EXISTS public.recurring_schedules_id_seq;
DROP TABLE IF EXISTS public.recurring_schedules;
DROP SEQUENCE IF EXISTS public.payments_id_seq;
DROP TABLE IF EXISTS public.payments;
DROP SEQUENCE IF EXISTS public.notifications_id_seq;
DROP TABLE IF EXISTS public.notifications;
DROP SEQUENCE IF EXISTS public.link_bio_pages_id_seq;
DROP TABLE IF EXISTS public.link_bio_pages;
DROP SEQUENCE IF EXISTS public.link_bio_links_id_seq;
DROP TABLE IF EXISTS public.link_bio_links;
DROP SEQUENCE IF EXISTS public.inbox_messages_id_seq;
DROP TABLE IF EXISTS public.inbox_messages;
DROP SEQUENCE IF EXISTS public.ideas_id_seq;
DROP TABLE IF EXISTS public.ideas;
DROP SEQUENCE IF EXISTS public.goals_id_seq;
DROP TABLE IF EXISTS public.goals;
DROP SEQUENCE IF EXISTS public.goal_milestones_id_seq;
DROP TABLE IF EXISTS public.goal_milestones;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP SEQUENCE IF EXISTS public.content_images_id_seq;
DROP TABLE IF EXISTS public.content_images;
DROP SEQUENCE IF EXISTS public.competitors_id_seq;
DROP TABLE IF EXISTS public.competitors;
DROP SEQUENCE IF EXISTS public.comments_id_seq;
DROP TABLE IF EXISTS public.comments;
DROP SEQUENCE IF EXISTS public.channels_id_seq;
DROP TABLE IF EXISTS public.channels;
DROP SEQUENCE IF EXISTS public.brand_kits_id_seq;
DROP TABLE IF EXISTS public.brand_kits;
DROP SEQUENCE IF EXISTS public.automation_workflows_id_seq;
DROP TABLE IF EXISTS public.automation_workflows;
DROP SEQUENCE IF EXISTS public.automation_rules_id_seq;
DROP TABLE IF EXISTS public.automation_rules;
DROP SEQUENCE IF EXISTS public.assets_id_seq;
DROP TABLE IF EXISTS public.assets;
DROP SEQUENCE IF EXISTS public.approvals_id_seq;
DROP TABLE IF EXISTS public.approvals;
DROP SEQUENCE IF EXISTS public.approval_comments_id_seq;
DROP TABLE IF EXISTS public.approval_comments;
DROP SEQUENCE IF EXISTS public.approval_chains_id_seq;
DROP TABLE IF EXISTS public.approval_chains;
DROP TABLE IF EXISTS public.analytics_daily_2026_12;
DROP TABLE IF EXISTS public.analytics_daily_2026_11;
DROP TABLE IF EXISTS public.analytics_daily_2026_10;
DROP TABLE IF EXISTS public.analytics_daily_2026_09;
DROP TABLE IF EXISTS public.analytics_daily_2026_08;
DROP TABLE IF EXISTS public.analytics_daily_2026_07;
DROP TABLE IF EXISTS public.analytics_daily_2026_06;
DROP TABLE IF EXISTS public.analytics_daily_2026_05;
DROP TABLE IF EXISTS public.analytics_daily_2026_04;
DROP TABLE IF EXISTS public.analytics_daily_2026_03;
DROP TABLE IF EXISTS public.analytics_daily_2026_02;
DROP TABLE IF EXISTS public.analytics_daily_2026_01;
DROP SEQUENCE IF EXISTS public.analytics_daily_id_seq;
DROP TABLE IF EXISTS public.analytics_daily;
DROP SEQUENCE IF EXISTS public.ai_purchased_credits_id_seq;
DROP TABLE IF EXISTS public.ai_purchased_credits;
DROP SEQUENCE IF EXISTS public.ai_credits_id_seq;
DROP TABLE IF EXISTS public.ai_credits;
DROP SEQUENCE IF EXISTS public.ai_credit_transactions_id_seq;
DROP TABLE IF EXISTS public.ai_credit_transactions;
DROP SEQUENCE IF EXISTS public.activity_logs_id_seq;
DROP TABLE IF EXISTS public.activity_logs;
DROP SEQUENCE IF EXISTS public.ab_tests_id_seq;
DROP TABLE IF EXISTS public.ab_tests;
DROP SEQUENCE IF EXISTS public.ab_test_variants_id_seq;
DROP TABLE IF EXISTS public.ab_test_variants;
DROP FUNCTION IF EXISTS public.fn_update_timestamp();
DROP FUNCTION IF EXISTS public.fn_create_analytics_partitions(months_ahead integer);
DROP TYPE IF EXISTS public.visibility_type;
DROP TYPE IF EXISTS public.video_status;
DROP TYPE IF EXISTS public.variant_status;
DROP TYPE IF EXISTS public.user_role;
DROP TYPE IF EXISTS public.upload_status;
DROP TYPE IF EXISTS public.subscription_status;
DROP TYPE IF EXISTS public.schedule_status;
DROP TYPE IF EXISTS public.purchased_credit_status;
DROP TYPE IF EXISTS public.processing_stage;
DROP TYPE IF EXISTS public.platform_type;
DROP TYPE IF EXISTS public.plan_type;
DROP TYPE IF EXISTS public.payment_type;
DROP TYPE IF EXISTS public.payment_status;
DROP TYPE IF EXISTS public.notification_type;
DROP TYPE IF EXISTS public.media_type;
DROP TYPE IF EXISTS public.credit_tx_type;
DROP TYPE IF EXISTS public.channel_status;
DROP TYPE IF EXISTS public.billing_cycle;
DROP TYPE IF EXISTS public.auth_provider;
--
-- Name: auth_provider; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.auth_provider AS ENUM (
    'GOOGLE',
    'KAKAO'
);


ALTER TYPE public.auth_provider OWNER TO ongo_user;

--
-- Name: billing_cycle; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.billing_cycle AS ENUM (
    'MONTHLY',
    'YEARLY'
);


ALTER TYPE public.billing_cycle OWNER TO ongo_user;

--
-- Name: channel_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.channel_status AS ENUM (
    'ACTIVE',
    'EXPIRED',
    'DISCONNECTED'
);


ALTER TYPE public.channel_status OWNER TO ongo_user;

--
-- Name: credit_tx_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.credit_tx_type AS ENUM (
    'DEDUCT',
    'CHARGE',
    'FREE_RESET',
    'REFUND'
);


ALTER TYPE public.credit_tx_type OWNER TO ongo_user;

--
-- Name: media_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.media_type AS ENUM (
    'VIDEO',
    'IMAGE'
);


ALTER TYPE public.media_type OWNER TO ongo_user;

--
-- Name: notification_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.notification_type AS ENUM (
    'UPLOAD_COMPLETE',
    'UPLOAD_FAILED',
    'CREDIT_LOW',
    'SCHEDULE_REMINDER',
    'COMMENT',
    'SYSTEM'
);


ALTER TYPE public.notification_type OWNER TO ongo_user;

--
-- Name: payment_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.payment_status AS ENUM (
    'PENDING',
    'COMPLETED',
    'FAILED',
    'REFUNDED'
);


ALTER TYPE public.payment_status OWNER TO ongo_user;

--
-- Name: payment_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.payment_type AS ENUM (
    'SUBSCRIPTION',
    'CREDIT'
);


ALTER TYPE public.payment_type OWNER TO ongo_user;

--
-- Name: plan_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.plan_type AS ENUM (
    'FREE',
    'STARTER',
    'PRO',
    'BUSINESS'
);


ALTER TYPE public.plan_type OWNER TO ongo_user;

--
-- Name: platform_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.platform_type AS ENUM (
    'YOUTUBE',
    'TIKTOK',
    'INSTAGRAM',
    'NAVER_CLIP',
    'TWITTER',
    'FACEBOOK',
    'THREADS',
    'PINTEREST',
    'LINKEDIN',
    'WORDPRESS',
    'TUMBLR',
    'VIMEO',
    'DAILYMOTION'
);


ALTER TYPE public.platform_type OWNER TO ongo_user;

--
-- Name: processing_stage; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.processing_stage AS ENUM (
    'PROBE',
    'TRANSCODE',
    'THUMBNAIL',
    'CAPTION',
    'UPLOAD'
);


ALTER TYPE public.processing_stage OWNER TO ongo_user;

--
-- Name: purchased_credit_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.purchased_credit_status AS ENUM (
    'ACTIVE',
    'EXPIRED',
    'EXHAUSTED'
);


ALTER TYPE public.purchased_credit_status OWNER TO ongo_user;

--
-- Name: schedule_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.schedule_status AS ENUM (
    'SCHEDULED',
    'PUBLISHED',
    'FAILED',
    'CANCELLED',
    'PROCESSING'
);


ALTER TYPE public.schedule_status OWNER TO ongo_user;

--
-- Name: subscription_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.subscription_status AS ENUM (
    'ACTIVE',
    'CANCELLED',
    'PAST_DUE',
    'FREE'
);


ALTER TYPE public.subscription_status OWNER TO ongo_user;

--
-- Name: upload_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.upload_status AS ENUM (
    'UPLOADING',
    'PROCESSING',
    'REVIEW',
    'PUBLISHED',
    'FAILED',
    'REJECTED',
    'DRAFT'
);


ALTER TYPE public.upload_status OWNER TO ongo_user;

--
-- Name: user_role; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.user_role AS ENUM (
    'USER',
    'ADMIN'
);


ALTER TYPE public.user_role OWNER TO ongo_user;

--
-- Name: variant_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.variant_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED'
);


ALTER TYPE public.variant_status OWNER TO ongo_user;

--
-- Name: video_status; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.video_status AS ENUM (
    'DRAFT',
    'UPLOADING',
    'PUBLISHED',
    'FAILED',
    'PROCESSING',
    'REVIEW',
    'REJECTED'
);


ALTER TYPE public.video_status OWNER TO ongo_user;

--
-- Name: visibility_type; Type: TYPE; Schema: public; Owner: ongo_user
--

CREATE TYPE public.visibility_type AS ENUM (
    'PUBLIC',
    'PRIVATE',
    'UNLISTED'
);


ALTER TYPE public.visibility_type OWNER TO ongo_user;

--
-- Name: fn_create_analytics_partitions(integer); Type: FUNCTION; Schema: public; Owner: ongo_user
--

CREATE FUNCTION public.fn_create_analytics_partitions(months_ahead integer DEFAULT 3) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
    i INTEGER;
BEGIN
    FOR i IN 0..months_ahead LOOP
        start_date := date_trunc('month', CURRENT_DATE + (i || ' months')::interval)::date;
        end_date := (start_date + interval '1 month')::date;
        partition_name := 'analytics_daily_' || to_char(start_date, 'YYYY_MM');

        IF NOT EXISTS (
            SELECT 1 FROM pg_class WHERE relname = partition_name
        ) THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF analytics_daily FOR VALUES FROM (%L) TO (%L)',
                partition_name, start_date, end_date
            );
            RAISE NOTICE 'Created partition: %', partition_name;
        END IF;
    END LOOP;
END;
$$;


ALTER FUNCTION public.fn_create_analytics_partitions(months_ahead integer) OWNER TO ongo_user;

--
-- Name: fn_update_timestamp(); Type: FUNCTION; Schema: public; Owner: ongo_user
--

CREATE FUNCTION public.fn_update_timestamp() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.fn_update_timestamp() OWNER TO ongo_user;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ab_test_variants; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.ab_test_variants (
    id bigint NOT NULL,
    test_id bigint NOT NULL,
    variant_name character varying(50) NOT NULL,
    title character varying(200),
    description text,
    thumbnail_url character varying(500),
    views bigint DEFAULT 0,
    clicks bigint DEFAULT 0,
    engagement_rate numeric(5,2) DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.ab_test_variants OWNER TO ongo_user;

--
-- Name: ab_test_variants_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.ab_test_variants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ab_test_variants_id_seq OWNER TO ongo_user;

--
-- Name: ab_test_variants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.ab_test_variants_id_seq OWNED BY public.ab_test_variants.id;


--
-- Name: ab_tests; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.ab_tests (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    video_id bigint,
    test_name character varying(200) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    metric_type character varying(30) DEFAULT 'CTR'::character varying,
    winner_variant_id bigint,
    started_at timestamp without time zone,
    ended_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.ab_tests OWNER TO ongo_user;

--
-- Name: ab_tests_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.ab_tests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ab_tests_id_seq OWNER TO ongo_user;

--
-- Name: ab_tests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.ab_tests_id_seq OWNED BY public.ab_tests.id;


--
-- Name: activity_logs; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.activity_logs (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    action character varying(50) NOT NULL,
    entity_type character varying(50),
    entity_id bigint,
    details jsonb DEFAULT '{}'::jsonb,
    ip_address character varying(45),
    user_agent character varying(500),
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.activity_logs OWNER TO ongo_user;

--
-- Name: activity_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.activity_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.activity_logs_id_seq OWNER TO ongo_user;

--
-- Name: activity_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.activity_logs_id_seq OWNED BY public.activity_logs.id;


--
-- Name: ai_credit_transactions; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.ai_credit_transactions (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type public.credit_tx_type NOT NULL,
    amount integer NOT NULL,
    balance_after integer NOT NULL,
    feature character varying(50),
    reference_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.ai_credit_transactions OWNER TO ongo_user;

--
-- Name: TABLE ai_credit_transactions; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.ai_credit_transactions IS '크레딧 트랜잭션 내역: DEDUCT(차감), CHARGE(충전), FREE_RESET(월초 리셋)';


--
-- Name: COLUMN ai_credit_transactions.amount; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.ai_credit_transactions.amount IS '양수: 충전, 음수: 차감';


--
-- Name: COLUMN ai_credit_transactions.feature; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.ai_credit_transactions.feature IS 'AI 기능 식별자: meta_gen, hashtag, stt, script_analysis, reply, schedule_suggest, ideas, report';


--
-- Name: ai_credit_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.ai_credit_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_credit_transactions_id_seq OWNER TO ongo_user;

--
-- Name: ai_credit_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.ai_credit_transactions_id_seq OWNED BY public.ai_credit_transactions.id;


--
-- Name: ai_credits; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.ai_credits (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    balance integer DEFAULT 0 NOT NULL,
    free_monthly integer DEFAULT 30 NOT NULL,
    free_remaining integer DEFAULT 30 NOT NULL,
    free_reset_date date NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_ai_credits_balance CHECK ((balance >= 0)),
    CONSTRAINT chk_ai_credits_free CHECK ((free_remaining >= 0)),
    CONSTRAINT chk_ai_credits_free_monthly CHECK ((free_monthly >= 0))
);


ALTER TABLE public.ai_credits OWNER TO ongo_user;

--
-- Name: TABLE ai_credits; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.ai_credits IS '사용자별 AI 크레딧 잔액 (비관적 잠금 FOR UPDATE 사용)';


--
-- Name: COLUMN ai_credits.free_monthly; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.ai_credits.free_monthly IS '플랜별 무료 월간 크레딧 (FREE:30, STARTER:100, PRO:300, BUSINESS:1000)';


--
-- Name: COLUMN ai_credits.free_reset_date; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.ai_credits.free_reset_date IS '무료 크레딧 리셋 날짜 (매월 1일)';


--
-- Name: ai_credits_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.ai_credits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_credits_id_seq OWNER TO ongo_user;

--
-- Name: ai_credits_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.ai_credits_id_seq OWNED BY public.ai_credits.id;


--
-- Name: ai_purchased_credits; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.ai_purchased_credits (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    package_name character varying(50) NOT NULL,
    total_credits integer NOT NULL,
    remaining integer NOT NULL,
    price integer NOT NULL,
    purchased_at timestamp without time zone DEFAULT now() NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    status public.purchased_credit_status DEFAULT 'ACTIVE'::public.purchased_credit_status NOT NULL,
    CONSTRAINT chk_purchased_credits_price CHECK ((price >= 0)),
    CONSTRAINT chk_purchased_credits_remaining CHECK ((remaining >= 0)),
    CONSTRAINT chk_purchased_credits_total CHECK ((total_credits > 0))
);


ALTER TABLE public.ai_purchased_credits OWNER TO ongo_user;

--
-- Name: TABLE ai_purchased_credits; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.ai_purchased_credits IS '구매 크레딧 패키지 (만료 임박순 FIFO 차감)';


--
-- Name: ai_purchased_credits_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.ai_purchased_credits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_purchased_credits_id_seq OWNER TO ongo_user;

--
-- Name: ai_purchased_credits_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.ai_purchased_credits_id_seq OWNED BY public.ai_purchased_credits.id;


--
-- Name: analytics_daily; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily (
    id bigint NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
)
PARTITION BY RANGE (date);


ALTER TABLE public.analytics_daily OWNER TO ongo_user;

--
-- Name: TABLE analytics_daily; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.analytics_daily IS '일별 영상 성과 데이터 (월별 파티셔닝)';


--
-- Name: COLUMN analytics_daily.revenue_micro; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.analytics_daily.revenue_micro IS '수익 (마이크로 단위: 1원 = 1,000,000 micro)';


--
-- Name: analytics_daily_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.analytics_daily_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.analytics_daily_id_seq OWNER TO ongo_user;

--
-- Name: analytics_daily_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.analytics_daily_id_seq OWNED BY public.analytics_daily.id;


--
-- Name: analytics_daily_2026_01; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_01 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_01 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_02; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_02 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_02 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_03; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_03 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_03 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_04; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_04 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_04 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_05; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_05 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_05 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_06; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_06 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_06 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_07; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_07 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_07 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_08; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_08 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_08 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_09; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_09 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_09 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_10; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_10 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_10 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_11; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_11 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_11 OWNER TO ongo_user;

--
-- Name: analytics_daily_2026_12; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.analytics_daily_2026_12 (
    id bigint DEFAULT nextval('public.analytics_daily_id_seq'::regclass) NOT NULL,
    video_upload_id bigint NOT NULL,
    date date NOT NULL,
    views integer DEFAULT 0 NOT NULL,
    likes integer DEFAULT 0 NOT NULL,
    comments_count integer DEFAULT 0 NOT NULL,
    shares integer DEFAULT 0 NOT NULL,
    watch_time_seconds bigint DEFAULT 0 NOT NULL,
    subscriber_gained integer DEFAULT 0 NOT NULL,
    revenue_micro bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_analytics_comments CHECK ((comments_count >= 0)),
    CONSTRAINT chk_analytics_likes CHECK ((likes >= 0)),
    CONSTRAINT chk_analytics_shares CHECK ((shares >= 0)),
    CONSTRAINT chk_analytics_views CHECK ((views >= 0)),
    CONSTRAINT chk_analytics_watch_time CHECK ((watch_time_seconds >= 0))
);


ALTER TABLE public.analytics_daily_2026_12 OWNER TO ongo_user;

--
-- Name: approval_chains; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.approval_chains (
    id bigint NOT NULL,
    approval_id bigint NOT NULL,
    step_order integer NOT NULL,
    approver_id bigint NOT NULL,
    approver_name character varying(100) NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    deadline_at timestamp without time zone,
    approved_at timestamp without time zone,
    comment text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.approval_chains OWNER TO ongo_user;

--
-- Name: TABLE approval_chains; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.approval_chains IS '멀티스텝 승인 체인 (단계별 승인자)';


--
-- Name: approval_chains_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.approval_chains_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.approval_chains_id_seq OWNER TO ongo_user;

--
-- Name: approval_chains_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.approval_chains_id_seq OWNED BY public.approval_chains.id;


--
-- Name: approval_comments; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.approval_comments (
    id bigint NOT NULL,
    approval_id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_name character varying(100) NOT NULL,
    content text NOT NULL,
    field character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.approval_comments OWNER TO ongo_user;

--
-- Name: TABLE approval_comments; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.approval_comments IS '승인 요청 코멘트';


--
-- Name: approval_comments_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.approval_comments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.approval_comments_id_seq OWNER TO ongo_user;

--
-- Name: approval_comments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.approval_comments_id_seq OWNED BY public.approval_comments.id;


--
-- Name: approvals; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.approvals (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    video_id bigint NOT NULL,
    video_title character varying(200) NOT NULL,
    platforms character varying(200) NOT NULL,
    scheduled_at timestamp without time zone,
    requester_id bigint NOT NULL,
    requester_name character varying(100) DEFAULT ''::character varying NOT NULL,
    reviewer_id bigint,
    reviewer_name character varying(100),
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    comment text,
    revision_note text,
    requested_at timestamp without time zone DEFAULT now() NOT NULL,
    decided_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.approvals OWNER TO ongo_user;

--
-- Name: TABLE approvals; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.approvals IS '콘텐츠 승인 요청';


--
-- Name: approvals_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.approvals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.approvals_id_seq OWNER TO ongo_user;

--
-- Name: approvals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.approvals_id_seq OWNED BY public.approvals.id;


--
-- Name: assets; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.assets (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    filename character varying(255) NOT NULL,
    original_filename character varying(255),
    file_url character varying(500) NOT NULL,
    file_type character varying(20) NOT NULL,
    file_size_bytes bigint,
    mime_type character varying(100),
    tags text[] DEFAULT '{}'::text[],
    folder character varying(100) DEFAULT 'default'::character varying,
    width integer,
    height integer,
    duration_seconds integer,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.assets OWNER TO ongo_user;

--
-- Name: assets_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.assets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.assets_id_seq OWNER TO ongo_user;

--
-- Name: assets_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.assets_id_seq OWNED BY public.assets.id;


--
-- Name: automation_rules; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.automation_rules (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    trigger_type character varying(50) NOT NULL,
    trigger_config jsonb DEFAULT '{}'::jsonb,
    action_type character varying(50) NOT NULL,
    action_config jsonb DEFAULT '{}'::jsonb,
    is_active boolean DEFAULT false,
    last_triggered_at timestamp without time zone,
    execution_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.automation_rules OWNER TO ongo_user;

--
-- Name: automation_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.automation_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.automation_rules_id_seq OWNER TO ongo_user;

--
-- Name: automation_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.automation_rules_id_seq OWNED BY public.automation_rules.id;


--
-- Name: automation_workflows; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.automation_workflows (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    trigger_type character varying(50) NOT NULL,
    trigger_config jsonb DEFAULT '{}'::jsonb,
    enabled boolean DEFAULT false,
    execution_count integer DEFAULT 0,
    last_executed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.automation_workflows OWNER TO ongo_user;

--
-- Name: automation_workflows_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.automation_workflows_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.automation_workflows_id_seq OWNER TO ongo_user;

--
-- Name: automation_workflows_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.automation_workflows_id_seq OWNED BY public.automation_workflows.id;


--
-- Name: brand_kits; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.brand_kits (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    primary_color character varying(7) DEFAULT '#7c3aed'::character varying,
    secondary_color character varying(7) DEFAULT '#1e40af'::character varying,
    accent_color character varying(7) DEFAULT '#059669'::character varying,
    font_family character varying(100) DEFAULT 'Pretendard'::character varying,
    logo_url character varying(500),
    intro_template_url character varying(500),
    outro_template_url character varying(500),
    watermark_url character varying(500),
    guidelines text,
    is_default boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.brand_kits OWNER TO ongo_user;

--
-- Name: brand_kits_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.brand_kits_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.brand_kits_id_seq OWNER TO ongo_user;

--
-- Name: brand_kits_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.brand_kits_id_seq OWNED BY public.brand_kits.id;


--
-- Name: channels; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.channels (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    platform public.platform_type NOT NULL,
    platform_channel_id character varying(255) NOT NULL,
    channel_name character varying(255) NOT NULL,
    channel_url text,
    subscriber_count integer DEFAULT 0,
    profile_image_url text,
    access_token text,
    refresh_token text,
    token_expires_at timestamp without time zone,
    status public.channel_status DEFAULT 'ACTIVE'::public.channel_status NOT NULL,
    connected_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.channels OWNER TO ongo_user;

--
-- Name: TABLE channels; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.channels IS '플랫폼 연동 채널 (YouTube/TikTok/Instagram/Naver Clip)';


--
-- Name: COLUMN channels.access_token; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.channels.access_token IS 'AES-256 암호화된 플랫폼 액세스 토큰';


--
-- Name: COLUMN channels.refresh_token; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.channels.refresh_token IS 'AES-256 암호화된 플랫폼 리프레시 토큰';


--
-- Name: channels_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.channels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.channels_id_seq OWNER TO ongo_user;

--
-- Name: channels_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.channels_id_seq OWNED BY public.channels.id;


--
-- Name: comments; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.comments (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    video_id bigint,
    platform character varying(20),
    platform_comment_id character varying(255),
    author_name character varying(100) NOT NULL,
    author_avatar_url character varying(500),
    content text NOT NULL,
    sentiment character varying(20) DEFAULT 'NEUTRAL'::character varying,
    is_replied boolean DEFAULT false,
    reply_content text,
    replied_at timestamp without time zone,
    published_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    like_count integer DEFAULT 0,
    reply_count integer DEFAULT 0,
    parent_comment_id bigint,
    platform_video_id character varying(255),
    platform_reply_id character varying(255),
    platform_like_id character varying(255),
    is_hidden boolean DEFAULT false,
    is_pinned boolean DEFAULT false,
    author_channel_url character varying(500),
    synced_at timestamp without time zone
);


ALTER TABLE public.comments OWNER TO ongo_user;

--
-- Name: comments_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.comments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.comments_id_seq OWNER TO ongo_user;

--
-- Name: comments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.comments_id_seq OWNED BY public.comments.id;


--
-- Name: competitors; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.competitors (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    platform character varying(20) NOT NULL,
    platform_channel_id character varying(255) NOT NULL,
    channel_name character varying(200) NOT NULL,
    channel_url character varying(500),
    subscriber_count bigint DEFAULT 0,
    total_views bigint DEFAULT 0,
    video_count integer DEFAULT 0,
    avg_views bigint DEFAULT 0,
    profile_image_url character varying(500),
    last_synced_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.competitors OWNER TO ongo_user;

--
-- Name: competitors_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.competitors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.competitors_id_seq OWNER TO ongo_user;

--
-- Name: competitors_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.competitors_id_seq OWNED BY public.competitors.id;


--
-- Name: content_images; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.content_images (
    id bigint NOT NULL,
    video_id bigint NOT NULL,
    image_url text NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    width integer,
    height integer,
    file_size_bytes bigint,
    original_filename text,
    content_type text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.content_images OWNER TO ongo_user;

--
-- Name: content_images_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.content_images_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.content_images_id_seq OWNER TO ongo_user;

--
-- Name: content_images_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.content_images_id_seq OWNED BY public.content_images.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO ongo_user;

--
-- Name: goal_milestones; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.goal_milestones (
    id bigint NOT NULL,
    goal_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    target_value bigint NOT NULL,
    is_reached boolean DEFAULT false,
    reached_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.goal_milestones OWNER TO ongo_user;

--
-- Name: goal_milestones_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.goal_milestones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.goal_milestones_id_seq OWNER TO ongo_user;

--
-- Name: goal_milestones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.goal_milestones_id_seq OWNED BY public.goal_milestones.id;


--
-- Name: goals; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.goals (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    metric_type character varying(30) NOT NULL,
    target_value bigint NOT NULL,
    current_value bigint DEFAULT 0,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.goals OWNER TO ongo_user;

--
-- Name: goals_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.goals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.goals_id_seq OWNER TO ongo_user;

--
-- Name: goals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.goals_id_seq OWNED BY public.goals.id;


--
-- Name: ideas; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.ideas (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    status character varying(20) DEFAULT 'BACKLOG'::character varying,
    category character varying(50),
    tags text[] DEFAULT '{}'::text[],
    priority character varying(10) DEFAULT 'MEDIUM'::character varying,
    source character varying(50),
    reference_url character varying(500),
    due_date date,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.ideas OWNER TO ongo_user;

--
-- Name: ideas_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.ideas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ideas_id_seq OWNER TO ongo_user;

--
-- Name: ideas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.ideas_id_seq OWNED BY public.ideas.id;


--
-- Name: inbox_messages; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.inbox_messages (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    platform character varying(20),
    sender_name character varying(100) NOT NULL,
    sender_avatar_url character varying(500),
    message_type character varying(20) DEFAULT 'COMMENT'::character varying,
    content text NOT NULL,
    is_read boolean DEFAULT false,
    is_starred boolean DEFAULT false,
    platform_message_id character varying(255),
    video_id bigint,
    received_at timestamp without time zone DEFAULT now(),
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.inbox_messages OWNER TO ongo_user;

--
-- Name: inbox_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.inbox_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inbox_messages_id_seq OWNER TO ongo_user;

--
-- Name: inbox_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.inbox_messages_id_seq OWNED BY public.inbox_messages.id;


--
-- Name: link_bio_links; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.link_bio_links (
    id bigint NOT NULL,
    page_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    url character varying(500) NOT NULL,
    icon character varying(50),
    sort_order integer DEFAULT 0,
    click_count bigint DEFAULT 0,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.link_bio_links OWNER TO ongo_user;

--
-- Name: link_bio_links_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.link_bio_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.link_bio_links_id_seq OWNER TO ongo_user;

--
-- Name: link_bio_links_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.link_bio_links_id_seq OWNED BY public.link_bio_links.id;


--
-- Name: link_bio_pages; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.link_bio_pages (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    title character varying(200),
    bio text,
    avatar_url character varying(500),
    theme character varying(30) DEFAULT 'default'::character varying,
    background_color character varying(7) DEFAULT '#ffffff'::character varying,
    text_color character varying(7) DEFAULT '#000000'::character varying,
    is_published boolean DEFAULT false,
    view_count bigint DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.link_bio_pages OWNER TO ongo_user;

--
-- Name: link_bio_pages_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.link_bio_pages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.link_bio_pages_id_seq OWNER TO ongo_user;

--
-- Name: link_bio_pages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.link_bio_pages_id_seq OWNED BY public.link_bio_pages.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type public.notification_type NOT NULL,
    title character varying(200) NOT NULL,
    message text,
    is_read boolean DEFAULT false NOT NULL,
    reference_type character varying(50),
    reference_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.notifications OWNER TO ongo_user;

--
-- Name: TABLE notifications; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.notifications IS '사용자 알림 (업로드 완료/실패, 크레딧 부족, 예약 리마인더, 댓글, 시스템)';


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.notifications_id_seq OWNER TO ongo_user;

--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.payments (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type public.payment_type NOT NULL,
    amount integer NOT NULL,
    currency character varying(3) DEFAULT 'KRW'::character varying NOT NULL,
    status public.payment_status DEFAULT 'PENDING'::public.payment_status NOT NULL,
    pg_provider character varying(50),
    pg_transaction_id character varying(100),
    payment_method character varying(50),
    receipt_url text,
    description text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_payments_amount CHECK ((amount > 0))
);


ALTER TABLE public.payments OWNER TO ongo_user;

--
-- Name: TABLE payments; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.payments IS '결제 이력 (구독 결제 + 크레딧 충전)';


--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.payments_id_seq OWNER TO ongo_user;

--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;


--
-- Name: recurring_schedules; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.recurring_schedules (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    frequency character varying(20) NOT NULL,
    day_of_week integer,
    day_of_month integer,
    time_of_day time without time zone NOT NULL,
    timezone character varying(50) DEFAULT 'Asia/Seoul'::character varying,
    platforms text[] DEFAULT '{}'::text[],
    title_template character varying(200),
    description_template text,
    tags text[] DEFAULT '{}'::text[],
    is_active boolean DEFAULT true,
    next_run_at timestamp without time zone,
    last_run_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.recurring_schedules OWNER TO ongo_user;

--
-- Name: recurring_schedules_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.recurring_schedules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.recurring_schedules_id_seq OWNER TO ongo_user;

--
-- Name: recurring_schedules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.recurring_schedules_id_seq OWNED BY public.recurring_schedules.id;


--
-- Name: recycling_suggestions; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.recycling_suggestions (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    video_id bigint NOT NULL,
    suggestion_type character varying(30) NOT NULL,
    reason text,
    suggested_platforms text[] DEFAULT '{}'::text[],
    priority_score integer DEFAULT 50,
    status character varying(20) DEFAULT 'PENDING'::character varying,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.recycling_suggestions OWNER TO ongo_user;

--
-- Name: recycling_suggestions_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.recycling_suggestions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.recycling_suggestions_id_seq OWNER TO ongo_user;

--
-- Name: recycling_suggestions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.recycling_suggestions_id_seq OWNED BY public.recycling_suggestions.id;


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.refresh_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token character varying(512) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.refresh_tokens OWNER TO ongo_user;

--
-- Name: TABLE refresh_tokens; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.refresh_tokens IS 'JWT 리프레시 토큰 (Access 30분, Refresh 7일)';


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.refresh_tokens_id_seq OWNER TO ongo_user;

--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.refresh_tokens_id_seq OWNED BY public.refresh_tokens.id;


--
-- Name: schedules; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.schedules (
    id bigint NOT NULL,
    video_id bigint NOT NULL,
    user_id bigint NOT NULL,
    scheduled_at timestamp without time zone NOT NULL,
    status public.schedule_status DEFAULT 'SCHEDULED'::public.schedule_status NOT NULL,
    platforms jsonb NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_schedules_future CHECK ((scheduled_at > created_at))
);


ALTER TABLE public.schedules OWNER TO ongo_user;

--
-- Name: TABLE schedules; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.schedules IS '영상 예약 업로드 관리';


--
-- Name: COLUMN schedules.platforms; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.schedules.platforms IS 'JSONB: 플랫폼별 개별 시간 설정 배열 [{platform, scheduled_at, config}]';


--
-- Name: schedules_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.schedules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.schedules_id_seq OWNER TO ongo_user;

--
-- Name: schedules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.schedules_id_seq OWNED BY public.schedules.id;


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.subscriptions (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    plan_type public.plan_type DEFAULT 'FREE'::public.plan_type NOT NULL,
    status public.subscription_status DEFAULT 'FREE'::public.subscription_status NOT NULL,
    price integer DEFAULT 0 NOT NULL,
    billing_cycle public.billing_cycle,
    current_period_start timestamp without time zone,
    current_period_end timestamp without time zone,
    next_billing_date timestamp without time zone,
    cancelled_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    storage_quota_limit_bytes bigint,
    CONSTRAINT chk_subscriptions_price CHECK ((price >= 0))
);


ALTER TABLE public.subscriptions OWNER TO ongo_user;

--
-- Name: TABLE subscriptions; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.subscriptions IS '사용자 구독 정보 (1인 1구독)';


--
-- Name: COLUMN subscriptions.storage_quota_limit_bytes; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.subscriptions.storage_quota_limit_bytes IS 'Admin override storage quota in bytes. NULL = use plan default.';


--
-- Name: subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.subscriptions_id_seq OWNER TO ongo_user;

--
-- Name: subscriptions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.subscriptions_id_seq OWNED BY public.subscriptions.id;


--
-- Name: team_members; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.team_members (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    member_email character varying(255) NOT NULL,
    member_name character varying(100),
    role character varying(20) DEFAULT 'VIEWER'::character varying,
    status character varying(20) DEFAULT 'INVITED'::character varying,
    permissions jsonb DEFAULT '{}'::jsonb,
    invited_at timestamp without time zone DEFAULT now(),
    joined_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.team_members OWNER TO ongo_user;

--
-- Name: team_members_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.team_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.team_members_id_seq OWNER TO ongo_user;

--
-- Name: team_members_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.team_members_id_seq OWNED BY public.team_members.id;


--
-- Name: templates; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.templates (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    title_template character varying(200),
    description_template text,
    tags text[] DEFAULT '{}'::text[],
    category character varying(50),
    platform character varying(20),
    usage_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.templates OWNER TO ongo_user;

--
-- Name: templates_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.templates_id_seq OWNER TO ongo_user;

--
-- Name: templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.templates_id_seq OWNED BY public.templates.id;


--
-- Name: user_settings; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.user_settings (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    default_visibility public.visibility_type DEFAULT 'PUBLIC'::public.visibility_type NOT NULL,
    default_platforms jsonb,
    default_ai_tone character varying(20) DEFAULT 'friendly'::character varying NOT NULL,
    notification_upload boolean DEFAULT true NOT NULL,
    notification_comment character varying(20) DEFAULT 'realtime'::character varying NOT NULL,
    notification_credit_threshold integer DEFAULT 20 NOT NULL,
    notification_schedule_reminder integer DEFAULT 60 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    default_ai_provider character varying(20) DEFAULT 'CLAUDE'::character varying NOT NULL,
    CONSTRAINT chk_default_ai_provider CHECK (((default_ai_provider)::text = ANY ((ARRAY['CLAUDE'::character varying, 'GEMINI'::character varying, 'OPENAI'::character varying])::text[]))),
    CONSTRAINT chk_settings_credit_threshold CHECK (((notification_credit_threshold >= 0) AND (notification_credit_threshold <= 100))),
    CONSTRAINT chk_settings_schedule_reminder CHECK ((notification_schedule_reminder > 0))
);


ALTER TABLE public.user_settings OWNER TO ongo_user;

--
-- Name: TABLE user_settings; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.user_settings IS '사용자 기본 설정';


--
-- Name: COLUMN user_settings.notification_comment; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.user_settings.notification_comment IS '댓글 알림 빈도: realtime, daily, off';


--
-- Name: COLUMN user_settings.notification_credit_threshold; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.user_settings.notification_credit_threshold IS '크레딧 잔여 알림 임계값 (%)';


--
-- Name: COLUMN user_settings.notification_schedule_reminder; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.user_settings.notification_schedule_reminder IS '예약 리마인더 사전 알림 시간 (분)';


--
-- Name: user_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.user_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.user_settings_id_seq OWNER TO ongo_user;

--
-- Name: user_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.user_settings_id_seq OWNED BY public.user_settings.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    name character varying(100) NOT NULL,
    nickname character varying(50),
    profile_image_url text,
    provider public.auth_provider NOT NULL,
    provider_id character varying(255) NOT NULL,
    plan_type public.plan_type DEFAULT 'FREE'::public.plan_type NOT NULL,
    category character varying(50),
    onboarding_completed boolean DEFAULT false NOT NULL,
    role public.user_role DEFAULT 'USER'::public.user_role NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.users OWNER TO ongo_user;

--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.users IS '사용자 계정 (소셜 로그인 전용: Google/Kakao)';


--
-- Name: COLUMN users.plan_type; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.users.plan_type IS '구독 플랜: FREE, STARTER(9,900원), PRO(19,900원), BUSINESS(49,900원)';


--
-- Name: COLUMN users.category; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.users.category IS '크리에이터 카테고리 (뷰티/먹방/게임/일상/교육/IT/여행/음악 등)';


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_id_seq OWNER TO ongo_user;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: video_media_info; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.video_media_info (
    id bigint NOT NULL,
    video_id bigint NOT NULL,
    video_codec text,
    width integer,
    height integer,
    fps double precision,
    bitrate_kbps integer,
    duration_ms bigint,
    color_space text,
    pixel_format text,
    profile text,
    audio_codec text,
    audio_bitrate_kbps integer,
    sample_rate integer,
    audio_channels integer,
    format_name text,
    file_size_bytes bigint,
    raw_json jsonb,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.video_media_info OWNER TO ongo_user;

--
-- Name: video_media_info_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.video_media_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.video_media_info_id_seq OWNER TO ongo_user;

--
-- Name: video_media_info_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.video_media_info_id_seq OWNED BY public.video_media_info.id;


--
-- Name: video_platform_meta; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.video_platform_meta (
    id bigint NOT NULL,
    video_upload_id bigint NOT NULL,
    title text,
    description text,
    tags text[],
    visibility public.visibility_type DEFAULT 'PUBLIC'::public.visibility_type NOT NULL,
    custom_thumbnail_url text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.video_platform_meta OWNER TO ongo_user;

--
-- Name: TABLE video_platform_meta; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.video_platform_meta IS '플랫폼별 메타데이터 커스터마이징 (AI 생성 결과 저장)';


--
-- Name: video_platform_meta_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.video_platform_meta_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.video_platform_meta_id_seq OWNER TO ongo_user;

--
-- Name: video_platform_meta_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.video_platform_meta_id_seq OWNED BY public.video_platform_meta.id;


--
-- Name: video_processing_progress; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.video_processing_progress (
    id bigint NOT NULL,
    video_id bigint NOT NULL,
    stage public.processing_stage NOT NULL,
    platform public.platform_type,
    progress_percent integer DEFAULT 0 NOT NULL,
    message text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.video_processing_progress OWNER TO ongo_user;

--
-- Name: video_processing_progress_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.video_processing_progress_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.video_processing_progress_id_seq OWNER TO ongo_user;

--
-- Name: video_processing_progress_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.video_processing_progress_id_seq OWNED BY public.video_processing_progress.id;


--
-- Name: video_subtitles; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.video_subtitles (
    id bigint NOT NULL,
    video_id bigint NOT NULL,
    language text DEFAULT 'ko'::text NOT NULL,
    format text DEFAULT 'srt'::text NOT NULL,
    content text NOT NULL,
    is_auto_generated boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.video_subtitles OWNER TO ongo_user;

--
-- Name: video_subtitles_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.video_subtitles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.video_subtitles_id_seq OWNER TO ongo_user;

--
-- Name: video_subtitles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.video_subtitles_id_seq OWNED BY public.video_subtitles.id;


--
-- Name: video_uploads; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.video_uploads (
    id bigint NOT NULL,
    video_id bigint NOT NULL,
    platform public.platform_type NOT NULL,
    platform_video_id character varying(255),
    status public.upload_status DEFAULT 'UPLOADING'::public.upload_status NOT NULL,
    error_message text,
    platform_url text,
    published_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.video_uploads OWNER TO ongo_user;

--
-- Name: TABLE video_uploads; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.video_uploads IS '플랫폼별 영상 업로드 상태 추적';


--
-- Name: COLUMN video_uploads.status; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.video_uploads.status IS 'UPLOADING → PROCESSING → REVIEW → PUBLISHED/FAILED/REJECTED';


--
-- Name: video_uploads_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.video_uploads_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.video_uploads_id_seq OWNER TO ongo_user;

--
-- Name: video_uploads_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.video_uploads_id_seq OWNED BY public.video_uploads.id;


--
-- Name: videos; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.videos (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    tags text[],
    category character varying(50),
    file_url text,
    file_size_bytes bigint,
    duration_seconds integer,
    resolution character varying(20),
    original_filename character varying(500),
    content_hash character varying(64),
    thumbnail_urls jsonb,
    status public.video_status DEFAULT 'DRAFT'::public.video_status NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    auto_thumbnails jsonb DEFAULT '[]'::jsonb,
    selected_thumbnail_index integer DEFAULT 0,
    media_type public.media_type DEFAULT 'VIDEO'::public.media_type NOT NULL,
    CONSTRAINT chk_videos_duration CHECK (((duration_seconds IS NULL) OR (duration_seconds > 0))),
    CONSTRAINT chk_videos_file_size CHECK (((file_size_bytes IS NULL) OR (file_size_bytes > 0)))
);


ALTER TABLE public.videos OWNER TO ongo_user;

--
-- Name: TABLE videos; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.videos IS '업로드 영상 원본 메타데이터';


--
-- Name: COLUMN videos.content_hash; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.videos.content_hash IS 'SHA-256 해시 - 중복 업로드 방지용';


--
-- Name: COLUMN videos.thumbnail_urls; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.videos.thumbnail_urls IS 'JSONB 배열: 자동 추출 썸네일 3장 URL';


--
-- Name: COLUMN videos.status; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON COLUMN public.videos.status IS 'DRAFT → UPLOADING → PUBLISHED/FAILED';


--
-- Name: videos_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.videos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.videos_id_seq OWNER TO ongo_user;

--
-- Name: videos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.videos_id_seq OWNED BY public.videos.id;


--
-- Name: watermarks; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.watermarks (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    image_url character varying(500) NOT NULL,
    "position" character varying(20) DEFAULT 'BOTTOM_RIGHT'::character varying,
    opacity numeric(3,2) DEFAULT 0.8,
    size integer DEFAULT 100,
    is_default boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.watermarks OWNER TO ongo_user;

--
-- Name: watermarks_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.watermarks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.watermarks_id_seq OWNER TO ongo_user;

--
-- Name: watermarks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.watermarks_id_seq OWNED BY public.watermarks.id;


--
-- Name: webhooks; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.webhooks (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    url character varying(500) NOT NULL,
    events text[] DEFAULT '{}'::text[],
    secret character varying(255),
    is_active boolean DEFAULT true,
    last_triggered_at timestamp without time zone,
    last_status_code integer,
    failure_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.webhooks OWNER TO ongo_user;

--
-- Name: webhooks_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.webhooks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.webhooks_id_seq OWNER TO ongo_user;

--
-- Name: webhooks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.webhooks_id_seq OWNED BY public.webhooks.id;


--
-- Name: weekly_digests; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.weekly_digests (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    week_start_date date NOT NULL,
    week_end_date date NOT NULL,
    summary text NOT NULL,
    top_videos text DEFAULT ''::text NOT NULL,
    anomalies text DEFAULT ''::text NOT NULL,
    action_items text DEFAULT ''::text NOT NULL,
    generated_at timestamp without time zone DEFAULT now() NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.weekly_digests OWNER TO ongo_user;

--
-- Name: TABLE weekly_digests; Type: COMMENT; Schema: public; Owner: ongo_user
--

COMMENT ON TABLE public.weekly_digests IS 'AI 주간 다이제스트 (매주 월요일 자동 생성, Pro/Business 전용)';


--
-- Name: weekly_digests_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.weekly_digests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.weekly_digests_id_seq OWNER TO ongo_user;

--
-- Name: weekly_digests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.weekly_digests_id_seq OWNED BY public.weekly_digests.id;


--
-- Name: workflow_actions; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.workflow_actions (
    id bigint NOT NULL,
    workflow_id bigint NOT NULL,
    action_type character varying(50) NOT NULL,
    config jsonb DEFAULT '{}'::jsonb,
    delay_minutes integer DEFAULT 0,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.workflow_actions OWNER TO ongo_user;

--
-- Name: workflow_actions_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.workflow_actions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workflow_actions_id_seq OWNER TO ongo_user;

--
-- Name: workflow_actions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.workflow_actions_id_seq OWNED BY public.workflow_actions.id;


--
-- Name: workflow_conditions; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.workflow_conditions (
    id bigint NOT NULL,
    workflow_id bigint NOT NULL,
    parent_condition_id bigint,
    group_type character varying(10) DEFAULT 'AND'::character varying,
    field character varying(200),
    operator character varying(30),
    value text,
    expression text,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.workflow_conditions OWNER TO ongo_user;

--
-- Name: workflow_conditions_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.workflow_conditions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workflow_conditions_id_seq OWNER TO ongo_user;

--
-- Name: workflow_conditions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.workflow_conditions_id_seq OWNED BY public.workflow_conditions.id;


--
-- Name: workflow_executions; Type: TABLE; Schema: public; Owner: ongo_user
--

CREATE TABLE public.workflow_executions (
    id bigint NOT NULL,
    workflow_id bigint NOT NULL,
    user_id bigint NOT NULL,
    trigger_data jsonb DEFAULT '{}'::jsonb,
    status character varying(20) DEFAULT 'RUNNING'::character varying,
    action_results jsonb DEFAULT '[]'::jsonb,
    error_message text,
    started_at timestamp without time zone DEFAULT now(),
    completed_at timestamp without time zone
);


ALTER TABLE public.workflow_executions OWNER TO ongo_user;

--
-- Name: workflow_executions_id_seq; Type: SEQUENCE; Schema: public; Owner: ongo_user
--

CREATE SEQUENCE public.workflow_executions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.workflow_executions_id_seq OWNER TO ongo_user;

--
-- Name: workflow_executions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ongo_user
--

ALTER SEQUENCE public.workflow_executions_id_seq OWNED BY public.workflow_executions.id;


--
-- Name: analytics_daily_2026_01; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_01 FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');


--
-- Name: analytics_daily_2026_02; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_02 FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');


--
-- Name: analytics_daily_2026_03; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_03 FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');


--
-- Name: analytics_daily_2026_04; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_04 FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');


--
-- Name: analytics_daily_2026_05; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_05 FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');


--
-- Name: analytics_daily_2026_06; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_06 FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');


--
-- Name: analytics_daily_2026_07; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_07 FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');


--
-- Name: analytics_daily_2026_08; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_08 FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');


--
-- Name: analytics_daily_2026_09; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_09 FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');


--
-- Name: analytics_daily_2026_10; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_10 FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');


--
-- Name: analytics_daily_2026_11; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_11 FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');


--
-- Name: analytics_daily_2026_12; Type: TABLE ATTACH; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ATTACH PARTITION public.analytics_daily_2026_12 FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');


--
-- Name: ab_test_variants id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_test_variants ALTER COLUMN id SET DEFAULT nextval('public.ab_test_variants_id_seq'::regclass);


--
-- Name: ab_tests id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_tests ALTER COLUMN id SET DEFAULT nextval('public.ab_tests_id_seq'::regclass);


--
-- Name: activity_logs id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.activity_logs ALTER COLUMN id SET DEFAULT nextval('public.activity_logs_id_seq'::regclass);


--
-- Name: ai_credit_transactions id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credit_transactions ALTER COLUMN id SET DEFAULT nextval('public.ai_credit_transactions_id_seq'::regclass);


--
-- Name: ai_credits id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credits ALTER COLUMN id SET DEFAULT nextval('public.ai_credits_id_seq'::regclass);


--
-- Name: ai_purchased_credits id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_purchased_credits ALTER COLUMN id SET DEFAULT nextval('public.ai_purchased_credits_id_seq'::regclass);


--
-- Name: analytics_daily id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily ALTER COLUMN id SET DEFAULT nextval('public.analytics_daily_id_seq'::regclass);


--
-- Name: approval_chains id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_chains ALTER COLUMN id SET DEFAULT nextval('public.approval_chains_id_seq'::regclass);


--
-- Name: approval_comments id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_comments ALTER COLUMN id SET DEFAULT nextval('public.approval_comments_id_seq'::regclass);


--
-- Name: approvals id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approvals ALTER COLUMN id SET DEFAULT nextval('public.approvals_id_seq'::regclass);


--
-- Name: assets id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.assets ALTER COLUMN id SET DEFAULT nextval('public.assets_id_seq'::regclass);


--
-- Name: automation_rules id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.automation_rules ALTER COLUMN id SET DEFAULT nextval('public.automation_rules_id_seq'::regclass);


--
-- Name: automation_workflows id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.automation_workflows ALTER COLUMN id SET DEFAULT nextval('public.automation_workflows_id_seq'::regclass);


--
-- Name: brand_kits id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.brand_kits ALTER COLUMN id SET DEFAULT nextval('public.brand_kits_id_seq'::regclass);


--
-- Name: channels id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.channels ALTER COLUMN id SET DEFAULT nextval('public.channels_id_seq'::regclass);


--
-- Name: comments id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.comments ALTER COLUMN id SET DEFAULT nextval('public.comments_id_seq'::regclass);


--
-- Name: competitors id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.competitors ALTER COLUMN id SET DEFAULT nextval('public.competitors_id_seq'::regclass);


--
-- Name: content_images id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.content_images ALTER COLUMN id SET DEFAULT nextval('public.content_images_id_seq'::regclass);


--
-- Name: goal_milestones id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.goal_milestones ALTER COLUMN id SET DEFAULT nextval('public.goal_milestones_id_seq'::regclass);


--
-- Name: goals id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.goals ALTER COLUMN id SET DEFAULT nextval('public.goals_id_seq'::regclass);


--
-- Name: ideas id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ideas ALTER COLUMN id SET DEFAULT nextval('public.ideas_id_seq'::regclass);


--
-- Name: inbox_messages id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.inbox_messages ALTER COLUMN id SET DEFAULT nextval('public.inbox_messages_id_seq'::regclass);


--
-- Name: link_bio_links id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_links ALTER COLUMN id SET DEFAULT nextval('public.link_bio_links_id_seq'::regclass);


--
-- Name: link_bio_pages id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_pages ALTER COLUMN id SET DEFAULT nextval('public.link_bio_pages_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);


--
-- Name: recurring_schedules id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recurring_schedules ALTER COLUMN id SET DEFAULT nextval('public.recurring_schedules_id_seq'::regclass);


--
-- Name: recycling_suggestions id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recycling_suggestions ALTER COLUMN id SET DEFAULT nextval('public.recycling_suggestions_id_seq'::regclass);


--
-- Name: refresh_tokens id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.refresh_tokens ALTER COLUMN id SET DEFAULT nextval('public.refresh_tokens_id_seq'::regclass);


--
-- Name: schedules id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.schedules ALTER COLUMN id SET DEFAULT nextval('public.schedules_id_seq'::regclass);


--
-- Name: subscriptions id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.subscriptions ALTER COLUMN id SET DEFAULT nextval('public.subscriptions_id_seq'::regclass);


--
-- Name: team_members id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.team_members ALTER COLUMN id SET DEFAULT nextval('public.team_members_id_seq'::regclass);


--
-- Name: templates id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.templates ALTER COLUMN id SET DEFAULT nextval('public.templates_id_seq'::regclass);


--
-- Name: user_settings id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.user_settings ALTER COLUMN id SET DEFAULT nextval('public.user_settings_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: video_media_info id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_media_info ALTER COLUMN id SET DEFAULT nextval('public.video_media_info_id_seq'::regclass);


--
-- Name: video_platform_meta id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_platform_meta ALTER COLUMN id SET DEFAULT nextval('public.video_platform_meta_id_seq'::regclass);


--
-- Name: video_processing_progress id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_processing_progress ALTER COLUMN id SET DEFAULT nextval('public.video_processing_progress_id_seq'::regclass);


--
-- Name: video_subtitles id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_subtitles ALTER COLUMN id SET DEFAULT nextval('public.video_subtitles_id_seq'::regclass);


--
-- Name: video_uploads id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_uploads ALTER COLUMN id SET DEFAULT nextval('public.video_uploads_id_seq'::regclass);


--
-- Name: videos id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.videos ALTER COLUMN id SET DEFAULT nextval('public.videos_id_seq'::regclass);


--
-- Name: watermarks id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.watermarks ALTER COLUMN id SET DEFAULT nextval('public.watermarks_id_seq'::regclass);


--
-- Name: webhooks id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.webhooks ALTER COLUMN id SET DEFAULT nextval('public.webhooks_id_seq'::regclass);


--
-- Name: weekly_digests id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.weekly_digests ALTER COLUMN id SET DEFAULT nextval('public.weekly_digests_id_seq'::regclass);


--
-- Name: workflow_actions id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_actions ALTER COLUMN id SET DEFAULT nextval('public.workflow_actions_id_seq'::regclass);


--
-- Name: workflow_conditions id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_conditions ALTER COLUMN id SET DEFAULT nextval('public.workflow_conditions_id_seq'::regclass);


--
-- Name: workflow_executions id; Type: DEFAULT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_executions ALTER COLUMN id SET DEFAULT nextval('public.workflow_executions_id_seq'::regclass);


--
-- Data for Name: ab_test_variants; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.ab_test_variants (id, test_id, variant_name, title, description, thumbnail_url, views, clicks, engagement_rate, created_at) FROM stdin;
1	1	A안	제목A	설명A	\N	0	0	0.00	2026-02-14 02:32:51.434629
2	2	A안	제목A	\N	\N	0	0	0.00	2026-02-14 02:35:29.9709
3	2	B안	제목B	\N	\N	0	0	0.00	2026-02-14 02:35:29.978409
\.


--
-- Data for Name: ab_tests; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.ab_tests (id, user_id, video_id, test_name, status, metric_type, winner_variant_id, started_at, ended_at, created_at) FROM stdin;
1	1	1	제목 AB 테스트	DRAFT	CTR	\N	\N	\N	2026-02-14 02:32:51.426555
2	1	1	제목 AB 테스트	DRAFT	CTR	\N	\N	\N	2026-02-14 02:35:29.959331
\.


--
-- Data for Name: activity_logs; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.activity_logs (id, user_id, action, entity_type, entity_id, details, ip_address, user_agent, created_at) FROM stdin;
\.


--
-- Data for Name: ai_credit_transactions; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.ai_credit_transactions (id, user_id, type, amount, balance_after, feature, reference_id, created_at) FROM stdin;
\.


--
-- Data for Name: ai_credits; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.ai_credits (id, user_id, balance, free_monthly, free_remaining, free_reset_date, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: ai_purchased_credits; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.ai_purchased_credits (id, user_id, package_name, total_credits, remaining, price, purchased_at, expires_at, status) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_01; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_01 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_02; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_02 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_03; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_03 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_04; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_04 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_05; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_05 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_06; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_06 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_07; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_07 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_08; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_08 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_09; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_09 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_10; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_10 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_11; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_11 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: analytics_daily_2026_12; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.analytics_daily_2026_12 (id, video_upload_id, date, views, likes, comments_count, shares, watch_time_seconds, subscriber_gained, revenue_micro, created_at) FROM stdin;
\.


--
-- Data for Name: approval_chains; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.approval_chains (id, approval_id, step_order, approver_id, approver_name, status, deadline_at, approved_at, comment, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: approval_comments; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.approval_comments (id, approval_id, user_id, user_name, content, field, created_at) FROM stdin;
1	1	1	사용자	수정이 필요합니다	title	2026-02-14 02:36:05.588847
\.


--
-- Data for Name: approvals; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.approvals (id, user_id, video_id, video_title, platforms, scheduled_at, requester_id, requester_name, reviewer_id, reviewer_name, status, comment, revision_note, requested_at, decided_at, created_at, updated_at) FROM stdin;
1	1	1	테스트 영상	YOUTUBE,TIKTOK	\N	1		1	\N	APPROVED	승인합니다	\N	2026-02-14 02:32:51.400022	2026-02-14 02:36:36.194258	2026-02-14 02:32:51.400403	2026-02-14 02:36:36.199772
\.


--
-- Data for Name: assets; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.assets (id, user_id, filename, original_filename, file_url, file_type, file_size_bytes, mime_type, tags, folder, width, height, duration_seconds, created_at) FROM stdin;
1	1	98c0da21-87e7-4926-b74c-be0bd4a6b5ff_sunny.jpg	sunny.jpg	/storage/assets/98c0da21-87e7-4926-b74c-be0bd4a6b5ff_sunny.jpg	IMAGE	153455	image/jpeg	{}	brand-kit	\N	\N	\N	2026-02-14 01:55:59.96732
\.


--
-- Data for Name: automation_rules; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.automation_rules (id, user_id, name, description, trigger_type, trigger_config, action_type, action_config, is_active, last_triggered_at, execution_count, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: automation_workflows; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.automation_workflows (id, user_id, name, description, trigger_type, trigger_config, enabled, execution_count, last_executed_at, created_at, updated_at) FROM stdin;
1	1	Test Workflow	Auto workflow	VIDEO_UPLOADED	{"minDuration": 60}	t	0	\N	2026-02-14 02:24:19.369991	2026-02-14 02:24:19.369991
\.


--
-- Data for Name: brand_kits; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.brand_kits (id, user_id, name, primary_color, secondary_color, accent_color, font_family, logo_url, intro_template_url, outro_template_url, watermark_url, guidelines, is_default, created_at, updated_at) FROM stdin;
1	1		#5a6c6b	#832525	#059669	Noto Sans KR	\N	\N	\N	\N		f	2026-02-14 01:56:13.041356	2026-02-14 01:56:13.041356
\.


--
-- Data for Name: channels; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.channels (id, user_id, platform, platform_channel_id, channel_name, channel_url, subscriber_count, profile_image_url, access_token, refresh_token, token_expires_at, status, connected_at, updated_at) FROM stdin;
\.


--
-- Data for Name: comments; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.comments (id, user_id, video_id, platform, platform_comment_id, author_name, author_avatar_url, content, sentiment, is_replied, reply_content, replied_at, published_at, created_at, updated_at, like_count, reply_count, parent_comment_id, platform_video_id, platform_reply_id, platform_like_id, is_hidden, is_pinned, author_channel_url, synced_at) FROM stdin;
\.


--
-- Data for Name: competitors; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.competitors (id, user_id, platform, platform_channel_id, channel_name, channel_url, subscriber_count, total_views, video_count, avg_views, profile_image_url, last_synced_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: content_images; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.content_images (id, video_id, image_url, display_order, width, height, file_size_bytes, original_filename, content_type, created_at) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init schema	SQL	V1__init_schema.sql	1525080547	ongo_user	2026-02-14 01:32:36.958814	360	t
2	2	fix enums	SQL	V2__fix_enums.sql	-916574246	ongo_user	2026-02-14 01:32:37.344486	4	t
3	3	content tables	SQL	V3__content_tables.sql	1861390836	ongo_user	2026-02-14 01:32:37.357541	36	t
4	4	analytics tables	SQL	V4__analytics_tables.sql	406156136	ongo_user	2026-02-14 01:32:37.418518	36	t
5	5	collab tables	SQL	V5__collab_tables.sql	690599772	ongo_user	2026-02-14 01:32:37.476921	51	t
6	6	automation tables	SQL	V6__automation_tables.sql	-1695562407	ongo_user	2026-02-14 01:32:37.577504	29	t
7	7	tools tables	SQL	V7__tools_tables.sql	-1151953225	ongo_user	2026-02-14 01:32:37.616129	54	t
8	8	recycling tables	SQL	V8__recycling_tables.sql	-1094382011	ongo_user	2026-02-14 01:32:37.684119	10	t
9	9	video variants	SQL	V9__video_variants.sql	-1096398225	ongo_user	2026-02-14 01:32:37.702393	7	t
10	10	weekly digest and approval chain	SQL	V10__weekly_digest_and_approval_chain.sql	-274699645	ongo_user	2026-02-14 01:32:37.714425	31	t
11	10.2	automation workflows	SQL	V10_2__automation_workflows.sql	-1916496449	ongo_user	2026-02-14 01:32:37.753136	28	t
12	11	quality indexes	SQL	V11__quality_indexes.sql	1604733335	ongo_user	2026-02-14 01:32:37.78676	1	t
13	12	video processing upgrade	SQL	V12__video_processing_upgrade.sql	489398190	ongo_user	2026-02-16 00:44:45.617522	42	t
14	13	image support	SQL	V13__image_support.sql	-1807134848	ongo_user	2026-02-17 16:05:03.538215	53	t
15	14	new platforms	SQL	V14__new_platforms.sql	942950593	ongo_user	2026-02-17 16:05:03.620211	42	t
16	15	remove video variants	SQL	V15__remove_video_variants.sql	257864997	ongo_user	2026-02-17 16:05:03.72452	82	t
17	16	content hash unique	SQL	V16__content_hash_unique.sql	-1570366127	ongo_user	2026-02-17 16:05:03.860366	13	t
18	17	comment management upgrade	SQL	V17__comment_management_upgrade.sql	-1732831031	ongo_user	2026-02-17 16:05:03.885897	25	t
19	18	storage quota admin	SQL	V18__storage_quota_admin.sql	-1453033711	ongo_user	2026-02-18 18:05:35.623557	44	t
20	19	ai provider selection	SQL	V19__ai_provider_selection.sql	281892234	ongo_user	2026-02-18 18:05:35.701568	15	t
\.


--
-- Data for Name: goal_milestones; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.goal_milestones (id, goal_id, title, target_value, is_reached, reached_at, created_at) FROM stdin;
\.


--
-- Data for Name: goals; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.goals (id, user_id, title, description, metric_type, target_value, current_value, start_date, end_date, status, created_at, updated_at) FROM stdin;
1	1	10K Subscribers	Reach 10K subs on YouTube	SUBSCRIBERS	10000	0	2026-01-01	2026-06-30	ACTIVE	2026-02-14 02:22:31.600164	2026-02-14 02:22:31.600164
\.


--
-- Data for Name: ideas; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.ideas (id, user_id, title, description, status, category, tags, priority, source, reference_url, due_date, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: inbox_messages; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.inbox_messages (id, user_id, platform, sender_name, sender_avatar_url, message_type, content, is_read, is_starred, platform_message_id, video_id, received_at, created_at) FROM stdin;
\.


--
-- Data for Name: link_bio_links; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.link_bio_links (id, page_id, title, url, icon, sort_order, click_count, is_active, created_at) FROM stdin;
\.


--
-- Data for Name: link_bio_pages; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.link_bio_pages (id, user_id, slug, title, bio, avatar_url, theme, background_color, text_color, is_published, view_count, created_at, updated_at) FROM stdin;
1	1	admin-page-updated	Updated Page	Updated bio	\N	dark	#000000	#ffffff	f	0	2026-02-14 02:24:19.44277	2026-02-14 02:24:19.471667
\.


--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.notifications (id, user_id, type, title, message, is_read, reference_type, reference_id, created_at) FROM stdin;
\.


--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.payments (id, user_id, type, amount, currency, status, pg_provider, pg_transaction_id, payment_method, receipt_url, description, created_at) FROM stdin;
\.


--
-- Data for Name: recurring_schedules; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.recurring_schedules (id, user_id, name, frequency, day_of_week, day_of_month, time_of_day, timezone, platforms, title_template, description_template, tags, is_active, next_run_at, last_run_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: recycling_suggestions; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.recycling_suggestions (id, user_id, video_id, suggestion_type, reason, suggested_platforms, priority_score, status, created_at) FROM stdin;
\.


--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.refresh_tokens (id, user_id, token, expires_at, created_at) FROM stdin;
39	1	eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2giLCJpYXQiOjE3NzEzMzYwMDMsImV4cCI6MTc3MTk0MDgwM30.RoNsnrIkNUgMFw5BCZxR_4oqgjI9cuUD9nxnfh8XnXF1rIw1GeyLPQo6T1k3LV8X	2026-02-24 22:46:43.386088	2026-02-17 22:46:43.386936
\.


--
-- Data for Name: schedules; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.schedules (id, video_id, user_id, scheduled_at, status, platforms, created_at, updated_at) FROM stdin;
1	1	1	2026-03-01 10:00:00	SCHEDULED	{"YOUTUBE": {"scheduledAt": "2026-03-01T10:00"}}	2026-02-14 02:36:24.947444	2026-02-14 02:36:24.947444
\.


--
-- Data for Name: subscriptions; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.subscriptions (id, user_id, plan_type, status, price, billing_cycle, current_period_start, current_period_end, next_billing_date, cancelled_at, created_at, updated_at, storage_quota_limit_bytes) FROM stdin;
1	1	BUSINESS	ACTIVE	0	MONTHLY	\N	\N	\N	\N	2026-02-13 17:31:14.298458	2026-02-13 17:31:14.298458	\N
\.


--
-- Data for Name: team_members; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.team_members (id, user_id, member_email, member_name, role, status, permissions, invited_at, joined_at, created_at) FROM stdin;
1	1	team@ongo.kr	\N	EDITOR	INVITED	{}	2026-02-14 02:36:36.105787	\N	2026-02-14 02:36:36.105787
\.


--
-- Data for Name: templates; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.templates (id, user_id, name, title_template, description_template, tags, category, platform, usage_count, created_at, updated_at) FROM stdin;
1	1	전범규의 일상	{{video_title}} {{date}}	\N	{}	title	YOUTUBE	3	2026-02-14 01:54:25.944089	2026-02-14 01:54:25.944089
3	1	4567	45674567	456745674	{4567456745,67,4567,45}	음악	\N	4	2026-02-17 17:28:50.372219	2026-02-17 17:28:50.372219
\.


--
-- Data for Name: user_settings; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.user_settings (id, user_id, default_visibility, default_platforms, default_ai_tone, notification_upload, notification_comment, notification_credit_threshold, notification_schedule_reminder, created_at, updated_at, default_ai_provider) FROM stdin;
1	1	PUBLIC	["YOUTUBE"]	FRIENDLY	t	realtime	20	60	2026-02-14 01:45:12.987789	2026-02-14 02:24:52.022863	CLAUDE
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.users (id, email, name, nickname, profile_image_url, provider, provider_id, plan_type, category, onboarding_completed, role, created_at, updated_at) FROM stdin;
1	admin@ongo.kr	Admin	관리자	\N	GOOGLE	dev-admin-001	BUSINESS	\N	t	ADMIN	2026-02-14 01:43:18.787013	2026-02-14 01:43:18.787013
\.


--
-- Data for Name: video_media_info; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.video_media_info (id, video_id, video_codec, width, height, fps, bitrate_kbps, duration_ms, color_space, pixel_format, profile, audio_codec, audio_bitrate_kbps, sample_rate, audio_channels, format_name, file_size_bytes, raw_json, created_at) FROM stdin;
\.


--
-- Data for Name: video_platform_meta; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.video_platform_meta (id, video_upload_id, title, description, tags, visibility, custom_thumbnail_url, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: video_processing_progress; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.video_processing_progress (id, video_id, stage, platform, progress_percent, message, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: video_subtitles; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.video_subtitles (id, video_id, language, format, content, is_auto_generated, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: video_uploads; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.video_uploads (id, video_id, platform, platform_video_id, status, error_message, platform_url, published_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: videos; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.videos (id, user_id, title, description, tags, category, file_url, file_size_bytes, duration_seconds, resolution, original_filename, content_hash, thumbnail_urls, status, created_at, updated_at, auto_thumbnails, selected_thumbnail_index, media_type) FROM stdin;
1	1	테스트 영상	설명입니다	{test,video}	\N	\N	1048576	\N	\N	test-video.mp4	\N	[]	DRAFT	2026-02-14 02:32:04.793019	2026-02-14 02:32:15.721269	[]	0	VIDEO
2	1	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1	\N	{}	\N	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-16 00:50:13.887335	2026-02-16 00:50:13.887335	[]	0	VIDEO
3	1	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1	\N	{}	\N	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-16 00:50:34.019006	2026-02-16 00:50:34.019006	[]	0	VIDEO
4	1	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1	\N	{}	\N	http://localhost:9000/ongo-videos/videos/4/db3c7a31-34ad-4e9c-8703-c11093d552a1.mp4	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	10b8156ef6fd5988618c644354098d400abd47dd97dff24ecc368a85e36d6e92	[]	DRAFT	2026-02-17 16:46:46.428753	2026-02-17 16:46:46.779064	[]	0	VIDEO
5	1	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1	\N	{}	\N	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-17 16:54:55.932204	2026-02-17 16:54:55.932204	[]	0	VIDEO
6	1	45674567	456745674	{4567456745,67,4567,45}	음악	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-17 17:28:31.94374	2026-02-17 17:29:00.562138	[]	0	VIDEO
7	1	45674567	456745674	{4567456745,67,4567,45}	음악	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-17 17:29:09.226904	2026-02-17 17:29:16.134407	[]	0	VIDEO
8	1	45674567	456745674	{4567456745,67,4567,45}	음악	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-17 17:44:06.130502	2026-02-17 17:44:17.457751	[]	0	VIDEO
9	1	45674567	456745674	{4567456745,67,4567,45}	음악	\N	3195071	\N	\N	watermarked-57b1928f-6629-415e-9cc3-fb9c99010cb1.mp4	\N	[]	DRAFT	2026-02-17 22:08:14.008618	2026-02-17 22:08:33.05332	[]	0	VIDEO
\.


--
-- Data for Name: watermarks; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.watermarks (id, user_id, name, image_url, "position", opacity, size, is_default, created_at) FROM stdin;
\.


--
-- Data for Name: webhooks; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.webhooks (id, user_id, name, url, events, secret, is_active, last_triggered_at, last_status_code, failure_count, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: weekly_digests; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.weekly_digests (id, user_id, week_start_date, week_end_date, summary, top_videos, anomalies, action_items, generated_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: workflow_actions; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.workflow_actions (id, workflow_id, action_type, config, delay_minutes, sort_order, created_at) FROM stdin;
1	1	SEND_NOTIFICATION	{"message": "New video uploaded"}	0	0	2026-02-14 02:24:19.382699
\.


--
-- Data for Name: workflow_conditions; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.workflow_conditions (id, workflow_id, parent_condition_id, group_type, field, operator, value, expression, sort_order, created_at) FROM stdin;
\.


--
-- Data for Name: workflow_executions; Type: TABLE DATA; Schema: public; Owner: ongo_user
--

COPY public.workflow_executions (id, workflow_id, user_id, trigger_data, status, action_results, error_message, started_at, completed_at) FROM stdin;
\.


--
-- Name: ab_test_variants_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.ab_test_variants_id_seq', 3, true);


--
-- Name: ab_tests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.ab_tests_id_seq', 2, true);


--
-- Name: activity_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.activity_logs_id_seq', 1, false);


--
-- Name: ai_credit_transactions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.ai_credit_transactions_id_seq', 1, false);


--
-- Name: ai_credits_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.ai_credits_id_seq', 1, false);


--
-- Name: ai_purchased_credits_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.ai_purchased_credits_id_seq', 1, false);


--
-- Name: analytics_daily_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.analytics_daily_id_seq', 1, false);


--
-- Name: approval_chains_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.approval_chains_id_seq', 1, false);


--
-- Name: approval_comments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.approval_comments_id_seq', 1, true);


--
-- Name: approvals_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.approvals_id_seq', 1, true);


--
-- Name: assets_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.assets_id_seq', 1, true);


--
-- Name: automation_rules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.automation_rules_id_seq', 1, true);


--
-- Name: automation_workflows_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.automation_workflows_id_seq', 1, true);


--
-- Name: brand_kits_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.brand_kits_id_seq', 2, true);


--
-- Name: channels_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.channels_id_seq', 1, false);


--
-- Name: comments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.comments_id_seq', 1, false);


--
-- Name: competitors_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.competitors_id_seq', 1, true);


--
-- Name: content_images_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.content_images_id_seq', 1, false);


--
-- Name: goal_milestones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.goal_milestones_id_seq', 1, false);


--
-- Name: goals_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.goals_id_seq', 1, true);


--
-- Name: ideas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.ideas_id_seq', 1, true);


--
-- Name: inbox_messages_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.inbox_messages_id_seq', 1, false);


--
-- Name: link_bio_links_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.link_bio_links_id_seq', 1, false);


--
-- Name: link_bio_pages_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.link_bio_pages_id_seq', 1, true);


--
-- Name: notifications_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.notifications_id_seq', 1, false);


--
-- Name: payments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.payments_id_seq', 1, false);


--
-- Name: recurring_schedules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.recurring_schedules_id_seq', 1, true);


--
-- Name: recycling_suggestions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.recycling_suggestions_id_seq', 1, false);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.refresh_tokens_id_seq', 39, true);


--
-- Name: schedules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.schedules_id_seq', 1, true);


--
-- Name: subscriptions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.subscriptions_id_seq', 1, true);


--
-- Name: team_members_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.team_members_id_seq', 1, true);


--
-- Name: templates_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.templates_id_seq', 3, true);


--
-- Name: user_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.user_settings_id_seq', 1, true);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.users_id_seq', 1, true);


--
-- Name: video_media_info_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.video_media_info_id_seq', 1, false);


--
-- Name: video_platform_meta_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.video_platform_meta_id_seq', 1, false);


--
-- Name: video_processing_progress_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.video_processing_progress_id_seq', 1, false);


--
-- Name: video_subtitles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.video_subtitles_id_seq', 1, false);


--
-- Name: video_uploads_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.video_uploads_id_seq', 1, false);


--
-- Name: videos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.videos_id_seq', 9, true);


--
-- Name: watermarks_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.watermarks_id_seq', 1, true);


--
-- Name: webhooks_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.webhooks_id_seq', 1, true);


--
-- Name: weekly_digests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.weekly_digests_id_seq', 1, false);


--
-- Name: workflow_actions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.workflow_actions_id_seq', 1, true);


--
-- Name: workflow_conditions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.workflow_conditions_id_seq', 1, false);


--
-- Name: workflow_executions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ongo_user
--

SELECT pg_catalog.setval('public.workflow_executions_id_seq', 1, false);


--
-- Name: ab_test_variants ab_test_variants_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_test_variants
    ADD CONSTRAINT ab_test_variants_pkey PRIMARY KEY (id);


--
-- Name: ab_tests ab_tests_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_tests
    ADD CONSTRAINT ab_tests_pkey PRIMARY KEY (id);


--
-- Name: activity_logs activity_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.activity_logs
    ADD CONSTRAINT activity_logs_pkey PRIMARY KEY (id);


--
-- Name: ai_credit_transactions ai_credit_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credit_transactions
    ADD CONSTRAINT ai_credit_transactions_pkey PRIMARY KEY (id);


--
-- Name: ai_credits ai_credits_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credits
    ADD CONSTRAINT ai_credits_pkey PRIMARY KEY (id);


--
-- Name: ai_credits ai_credits_user_id_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credits
    ADD CONSTRAINT ai_credits_user_id_key UNIQUE (user_id);


--
-- Name: ai_purchased_credits ai_purchased_credits_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_purchased_credits
    ADD CONSTRAINT ai_purchased_credits_pkey PRIMARY KEY (id);


--
-- Name: analytics_daily analytics_daily_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily
    ADD CONSTRAINT analytics_daily_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_01 analytics_daily_2026_01_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_01
    ADD CONSTRAINT analytics_daily_2026_01_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily uq_analytics_upload_date; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily
    ADD CONSTRAINT uq_analytics_upload_date UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_01 analytics_daily_2026_01_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_01
    ADD CONSTRAINT analytics_daily_2026_01_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_02 analytics_daily_2026_02_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_02
    ADD CONSTRAINT analytics_daily_2026_02_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_02 analytics_daily_2026_02_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_02
    ADD CONSTRAINT analytics_daily_2026_02_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_03 analytics_daily_2026_03_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_03
    ADD CONSTRAINT analytics_daily_2026_03_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_03 analytics_daily_2026_03_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_03
    ADD CONSTRAINT analytics_daily_2026_03_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_04 analytics_daily_2026_04_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_04
    ADD CONSTRAINT analytics_daily_2026_04_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_04 analytics_daily_2026_04_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_04
    ADD CONSTRAINT analytics_daily_2026_04_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_05 analytics_daily_2026_05_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_05
    ADD CONSTRAINT analytics_daily_2026_05_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_05 analytics_daily_2026_05_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_05
    ADD CONSTRAINT analytics_daily_2026_05_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_06 analytics_daily_2026_06_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_06
    ADD CONSTRAINT analytics_daily_2026_06_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_06 analytics_daily_2026_06_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_06
    ADD CONSTRAINT analytics_daily_2026_06_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_07 analytics_daily_2026_07_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_07
    ADD CONSTRAINT analytics_daily_2026_07_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_07 analytics_daily_2026_07_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_07
    ADD CONSTRAINT analytics_daily_2026_07_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_08 analytics_daily_2026_08_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_08
    ADD CONSTRAINT analytics_daily_2026_08_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_08 analytics_daily_2026_08_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_08
    ADD CONSTRAINT analytics_daily_2026_08_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_09 analytics_daily_2026_09_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_09
    ADD CONSTRAINT analytics_daily_2026_09_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_09 analytics_daily_2026_09_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_09
    ADD CONSTRAINT analytics_daily_2026_09_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_10 analytics_daily_2026_10_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_10
    ADD CONSTRAINT analytics_daily_2026_10_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_10 analytics_daily_2026_10_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_10
    ADD CONSTRAINT analytics_daily_2026_10_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_11 analytics_daily_2026_11_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_11
    ADD CONSTRAINT analytics_daily_2026_11_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_11 analytics_daily_2026_11_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_11
    ADD CONSTRAINT analytics_daily_2026_11_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: analytics_daily_2026_12 analytics_daily_2026_12_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_12
    ADD CONSTRAINT analytics_daily_2026_12_pkey PRIMARY KEY (id, date);


--
-- Name: analytics_daily_2026_12 analytics_daily_2026_12_video_upload_id_date_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.analytics_daily_2026_12
    ADD CONSTRAINT analytics_daily_2026_12_video_upload_id_date_key UNIQUE (video_upload_id, date);


--
-- Name: approval_chains approval_chains_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_chains
    ADD CONSTRAINT approval_chains_pkey PRIMARY KEY (id);


--
-- Name: approval_comments approval_comments_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_comments
    ADD CONSTRAINT approval_comments_pkey PRIMARY KEY (id);


--
-- Name: approvals approvals_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_pkey PRIMARY KEY (id);


--
-- Name: assets assets_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_pkey PRIMARY KEY (id);


--
-- Name: automation_rules automation_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.automation_rules
    ADD CONSTRAINT automation_rules_pkey PRIMARY KEY (id);


--
-- Name: automation_workflows automation_workflows_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.automation_workflows
    ADD CONSTRAINT automation_workflows_pkey PRIMARY KEY (id);


--
-- Name: brand_kits brand_kits_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.brand_kits
    ADD CONSTRAINT brand_kits_pkey PRIMARY KEY (id);


--
-- Name: channels channels_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT channels_pkey PRIMARY KEY (id);


--
-- Name: comments comments_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_pkey PRIMARY KEY (id);


--
-- Name: competitors competitors_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.competitors
    ADD CONSTRAINT competitors_pkey PRIMARY KEY (id);


--
-- Name: content_images content_images_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.content_images
    ADD CONSTRAINT content_images_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: goal_milestones goal_milestones_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.goal_milestones
    ADD CONSTRAINT goal_milestones_pkey PRIMARY KEY (id);


--
-- Name: goals goals_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.goals
    ADD CONSTRAINT goals_pkey PRIMARY KEY (id);


--
-- Name: ideas ideas_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ideas
    ADD CONSTRAINT ideas_pkey PRIMARY KEY (id);


--
-- Name: inbox_messages inbox_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.inbox_messages
    ADD CONSTRAINT inbox_messages_pkey PRIMARY KEY (id);


--
-- Name: link_bio_links link_bio_links_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_links
    ADD CONSTRAINT link_bio_links_pkey PRIMARY KEY (id);


--
-- Name: link_bio_pages link_bio_pages_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_pages
    ADD CONSTRAINT link_bio_pages_pkey PRIMARY KEY (id);


--
-- Name: link_bio_pages link_bio_pages_slug_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_pages
    ADD CONSTRAINT link_bio_pages_slug_key UNIQUE (slug);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: recurring_schedules recurring_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recurring_schedules
    ADD CONSTRAINT recurring_schedules_pkey PRIMARY KEY (id);


--
-- Name: recycling_suggestions recycling_suggestions_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recycling_suggestions
    ADD CONSTRAINT recycling_suggestions_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_key UNIQUE (token);


--
-- Name: schedules schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.schedules
    ADD CONSTRAINT schedules_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_user_id_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_user_id_key UNIQUE (user_id);


--
-- Name: team_members team_members_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT team_members_pkey PRIMARY KEY (id);


--
-- Name: templates templates_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.templates
    ADD CONSTRAINT templates_pkey PRIMARY KEY (id);


--
-- Name: channels uq_channels_user_platform; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT uq_channels_user_platform UNIQUE (user_id, platform);


--
-- Name: users uq_users_provider; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_provider UNIQUE (provider, provider_id);


--
-- Name: video_uploads uq_video_uploads_video_platform; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_uploads
    ADD CONSTRAINT uq_video_uploads_video_platform UNIQUE (video_id, platform);


--
-- Name: user_settings user_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.user_settings
    ADD CONSTRAINT user_settings_pkey PRIMARY KEY (id);


--
-- Name: user_settings user_settings_user_id_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.user_settings
    ADD CONSTRAINT user_settings_user_id_key UNIQUE (user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: video_media_info video_media_info_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_media_info
    ADD CONSTRAINT video_media_info_pkey PRIMARY KEY (id);


--
-- Name: video_media_info video_media_info_video_id_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_media_info
    ADD CONSTRAINT video_media_info_video_id_key UNIQUE (video_id);


--
-- Name: video_platform_meta video_platform_meta_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_platform_meta
    ADD CONSTRAINT video_platform_meta_pkey PRIMARY KEY (id);


--
-- Name: video_processing_progress video_processing_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_processing_progress
    ADD CONSTRAINT video_processing_progress_pkey PRIMARY KEY (id);


--
-- Name: video_processing_progress video_processing_progress_video_id_stage_platform_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_processing_progress
    ADD CONSTRAINT video_processing_progress_video_id_stage_platform_key UNIQUE (video_id, stage, platform);


--
-- Name: video_subtitles video_subtitles_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_subtitles
    ADD CONSTRAINT video_subtitles_pkey PRIMARY KEY (id);


--
-- Name: video_subtitles video_subtitles_video_id_language_key; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_subtitles
    ADD CONSTRAINT video_subtitles_video_id_language_key UNIQUE (video_id, language);


--
-- Name: video_uploads video_uploads_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_uploads
    ADD CONSTRAINT video_uploads_pkey PRIMARY KEY (id);


--
-- Name: videos videos_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.videos
    ADD CONSTRAINT videos_pkey PRIMARY KEY (id);


--
-- Name: watermarks watermarks_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.watermarks
    ADD CONSTRAINT watermarks_pkey PRIMARY KEY (id);


--
-- Name: webhooks webhooks_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.webhooks
    ADD CONSTRAINT webhooks_pkey PRIMARY KEY (id);


--
-- Name: weekly_digests weekly_digests_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.weekly_digests
    ADD CONSTRAINT weekly_digests_pkey PRIMARY KEY (id);


--
-- Name: workflow_actions workflow_actions_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_actions
    ADD CONSTRAINT workflow_actions_pkey PRIMARY KEY (id);


--
-- Name: workflow_conditions workflow_conditions_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_conditions
    ADD CONSTRAINT workflow_conditions_pkey PRIMARY KEY (id);


--
-- Name: workflow_executions workflow_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_ab_test_variants_test_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_ab_test_variants_test_id ON public.ab_test_variants USING btree (test_id);


--
-- Name: idx_ab_tests_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_ab_tests_user_id ON public.ab_tests USING btree (user_id);


--
-- Name: idx_activity_logs_created_at; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_activity_logs_created_at ON public.activity_logs USING btree (created_at);


--
-- Name: idx_activity_logs_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_activity_logs_user_id ON public.activity_logs USING btree (user_id);


--
-- Name: idx_approval_chains_approval_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approval_chains_approval_id ON public.approval_chains USING btree (approval_id, step_order);


--
-- Name: idx_approval_chains_approver_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approval_chains_approver_id ON public.approval_chains USING btree (approver_id);


--
-- Name: idx_approval_chains_deadline; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approval_chains_deadline ON public.approval_chains USING btree (deadline_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_approval_comments_approval_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approval_comments_approval_id ON public.approval_comments USING btree (approval_id);


--
-- Name: idx_approvals_reviewer_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approvals_reviewer_id ON public.approvals USING btree (reviewer_id);


--
-- Name: idx_approvals_status; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approvals_status ON public.approvals USING btree (status);


--
-- Name: idx_approvals_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_approvals_user_id ON public.approvals USING btree (user_id);


--
-- Name: idx_assets_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_assets_user_id ON public.assets USING btree (user_id);


--
-- Name: idx_automation_rules_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_automation_rules_user_id ON public.automation_rules USING btree (user_id);


--
-- Name: idx_automation_workflows_enabled; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_automation_workflows_enabled ON public.automation_workflows USING btree (enabled) WHERE (enabled = true);


--
-- Name: idx_automation_workflows_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_automation_workflows_user_id ON public.automation_workflows USING btree (user_id);


--
-- Name: idx_brand_kits_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_brand_kits_user_id ON public.brand_kits USING btree (user_id);


--
-- Name: idx_channels_status; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_channels_status ON public.channels USING btree (user_id, status);


--
-- Name: idx_comments_parent; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_comments_parent ON public.comments USING btree (parent_comment_id) WHERE (parent_comment_id IS NOT NULL);


--
-- Name: idx_comments_platform_dedup; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE UNIQUE INDEX idx_comments_platform_dedup ON public.comments USING btree (platform, platform_comment_id) WHERE (platform_comment_id IS NOT NULL);


--
-- Name: idx_comments_platform_video; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_comments_platform_video ON public.comments USING btree (platform_video_id) WHERE (platform_video_id IS NOT NULL);


--
-- Name: idx_comments_synced_at; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_comments_synced_at ON public.comments USING btree (user_id, synced_at DESC);


--
-- Name: idx_comments_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_comments_user_id ON public.comments USING btree (user_id);


--
-- Name: idx_comments_video_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_comments_video_id ON public.comments USING btree (video_id);


--
-- Name: idx_competitors_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_competitors_user_id ON public.competitors USING btree (user_id);


--
-- Name: idx_content_images_video_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_content_images_video_id ON public.content_images USING btree (video_id);


--
-- Name: idx_credit_tx_user_date; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_credit_tx_user_date ON public.ai_credit_transactions USING btree (user_id, created_at DESC);


--
-- Name: idx_goal_milestones_goal_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_goal_milestones_goal_id ON public.goal_milestones USING btree (goal_id);


--
-- Name: idx_goals_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_goals_user_id ON public.goals USING btree (user_id);


--
-- Name: idx_ideas_status; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_ideas_status ON public.ideas USING btree (user_id, status);


--
-- Name: idx_ideas_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_ideas_user_id ON public.ideas USING btree (user_id);


--
-- Name: idx_inbox_messages_is_read; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_inbox_messages_is_read ON public.inbox_messages USING btree (user_id, is_read);


--
-- Name: idx_inbox_messages_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_inbox_messages_user_id ON public.inbox_messages USING btree (user_id);


--
-- Name: idx_link_bio_links_page_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_link_bio_links_page_id ON public.link_bio_links USING btree (page_id);


--
-- Name: idx_link_bio_pages_slug; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE UNIQUE INDEX idx_link_bio_pages_slug ON public.link_bio_pages USING btree (slug);


--
-- Name: idx_link_bio_pages_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_link_bio_pages_user_id ON public.link_bio_pages USING btree (user_id);


--
-- Name: idx_notifications_user_type; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_notifications_user_type ON public.notifications USING btree (user_id, type);


--
-- Name: idx_notifications_user_unread; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_notifications_user_unread ON public.notifications USING btree (user_id, is_read, created_at DESC);


--
-- Name: idx_payments_pg_tx; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_payments_pg_tx ON public.payments USING btree (pg_transaction_id) WHERE (pg_transaction_id IS NOT NULL);


--
-- Name: idx_payments_user_date; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_payments_user_date ON public.payments USING btree (user_id, created_at DESC);


--
-- Name: idx_purchased_user_active; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_purchased_user_active ON public.ai_purchased_credits USING btree (user_id, status, expires_at) WHERE (status = 'ACTIVE'::public.purchased_credit_status);


--
-- Name: idx_recurring_schedules_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_recurring_schedules_user_id ON public.recurring_schedules USING btree (user_id);


--
-- Name: idx_recycling_suggestions_status; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_recycling_suggestions_status ON public.recycling_suggestions USING btree (user_id, status);


--
-- Name: idx_recycling_suggestions_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_recycling_suggestions_user_id ON public.recycling_suggestions USING btree (user_id);


--
-- Name: idx_refresh_tokens_expires; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_refresh_tokens_expires ON public.refresh_tokens USING btree (expires_at);


--
-- Name: idx_refresh_tokens_user; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_refresh_tokens_user ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_schedules_pending; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_schedules_pending ON public.schedules USING btree (scheduled_at, status) WHERE (status = 'SCHEDULED'::public.schedule_status);


--
-- Name: idx_schedules_user; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_schedules_user ON public.schedules USING btree (user_id);


--
-- Name: idx_schedules_video; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_schedules_video ON public.schedules USING btree (video_id);


--
-- Name: idx_subscriptions_next_billing; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_subscriptions_next_billing ON public.subscriptions USING btree (next_billing_date) WHERE (status = 'ACTIVE'::public.subscription_status);


--
-- Name: idx_team_members_user_email; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE UNIQUE INDEX idx_team_members_user_email ON public.team_members USING btree (user_id, member_email);


--
-- Name: idx_team_members_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_team_members_user_id ON public.team_members USING btree (user_id);


--
-- Name: idx_templates_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_templates_user_id ON public.templates USING btree (user_id);


--
-- Name: idx_users_role; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_users_role ON public.users USING btree (role);


--
-- Name: idx_video_media_info_video_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_media_info_video_id ON public.video_media_info USING btree (video_id);


--
-- Name: idx_video_platform_meta_upload; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_platform_meta_upload ON public.video_platform_meta USING btree (video_upload_id);


--
-- Name: idx_video_processing_progress_video_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_processing_progress_video_id ON public.video_processing_progress USING btree (video_id);


--
-- Name: idx_video_subtitles_video_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_subtitles_video_id ON public.video_subtitles USING btree (video_id);


--
-- Name: idx_video_uploads_active; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_uploads_active ON public.video_uploads USING btree (status) WHERE (status = ANY (ARRAY['UPLOADING'::public.upload_status, 'PROCESSING'::public.upload_status]));


--
-- Name: idx_video_uploads_platform_video; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_uploads_platform_video ON public.video_uploads USING btree (platform_video_id) WHERE (platform_video_id IS NOT NULL);


--
-- Name: idx_video_uploads_video_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_video_uploads_video_id ON public.video_uploads USING btree (video_id);


--
-- Name: idx_videos_media_type; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_videos_media_type ON public.videos USING btree (media_type);


--
-- Name: idx_videos_status; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_videos_status ON public.videos USING btree (status);


--
-- Name: idx_videos_user_content_hash; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE UNIQUE INDEX idx_videos_user_content_hash ON public.videos USING btree (user_id, content_hash) WHERE (content_hash IS NOT NULL);


--
-- Name: idx_videos_user_created; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_videos_user_created ON public.videos USING btree (user_id, created_at DESC);


--
-- Name: idx_watermarks_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_watermarks_user_id ON public.watermarks USING btree (user_id);


--
-- Name: idx_webhooks_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_webhooks_user_id ON public.webhooks USING btree (user_id);


--
-- Name: idx_weekly_digests_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_weekly_digests_user_id ON public.weekly_digests USING btree (user_id, created_at DESC);


--
-- Name: idx_weekly_digests_user_week; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE UNIQUE INDEX idx_weekly_digests_user_week ON public.weekly_digests USING btree (user_id, week_start_date);


--
-- Name: idx_workflow_actions_workflow_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_workflow_actions_workflow_id ON public.workflow_actions USING btree (workflow_id);


--
-- Name: idx_workflow_conditions_parent; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_workflow_conditions_parent ON public.workflow_conditions USING btree (parent_condition_id);


--
-- Name: idx_workflow_conditions_workflow_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_workflow_conditions_workflow_id ON public.workflow_conditions USING btree (workflow_id);


--
-- Name: idx_workflow_executions_user_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_workflow_executions_user_id ON public.workflow_executions USING btree (user_id);


--
-- Name: idx_workflow_executions_workflow_id; Type: INDEX; Schema: public; Owner: ongo_user
--

CREATE INDEX idx_workflow_executions_workflow_id ON public.workflow_executions USING btree (workflow_id);


--
-- Name: analytics_daily_2026_01_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_01_pkey;


--
-- Name: analytics_daily_2026_01_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_01_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_02_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_02_pkey;


--
-- Name: analytics_daily_2026_02_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_02_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_03_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_03_pkey;


--
-- Name: analytics_daily_2026_03_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_03_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_04_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_04_pkey;


--
-- Name: analytics_daily_2026_04_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_04_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_05_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_05_pkey;


--
-- Name: analytics_daily_2026_05_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_05_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_06_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_06_pkey;


--
-- Name: analytics_daily_2026_06_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_06_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_07_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_07_pkey;


--
-- Name: analytics_daily_2026_07_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_07_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_08_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_08_pkey;


--
-- Name: analytics_daily_2026_08_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_08_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_09_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_09_pkey;


--
-- Name: analytics_daily_2026_09_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_09_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_10_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_10_pkey;


--
-- Name: analytics_daily_2026_10_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_10_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_11_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_11_pkey;


--
-- Name: analytics_daily_2026_11_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_11_video_upload_id_date_key;


--
-- Name: analytics_daily_2026_12_pkey; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.analytics_daily_pkey ATTACH PARTITION public.analytics_daily_2026_12_pkey;


--
-- Name: analytics_daily_2026_12_video_upload_id_date_key; Type: INDEX ATTACH; Schema: public; Owner: ongo_user
--

ALTER INDEX public.uq_analytics_upload_date ATTACH PARTITION public.analytics_daily_2026_12_video_upload_id_date_key;


--
-- Name: ai_credits trg_ai_credits_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_ai_credits_updated_at BEFORE UPDATE ON public.ai_credits FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: approval_chains trg_approval_chains_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_approval_chains_updated_at BEFORE UPDATE ON public.approval_chains FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: approvals trg_approvals_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_approvals_updated_at BEFORE UPDATE ON public.approvals FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: channels trg_channels_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_channels_updated_at BEFORE UPDATE ON public.channels FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: schedules trg_schedules_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_schedules_updated_at BEFORE UPDATE ON public.schedules FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: subscriptions trg_subscriptions_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_subscriptions_updated_at BEFORE UPDATE ON public.subscriptions FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: user_settings trg_user_settings_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_user_settings_updated_at BEFORE UPDATE ON public.user_settings FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: users trg_users_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: video_platform_meta trg_video_platform_meta_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_video_platform_meta_updated_at BEFORE UPDATE ON public.video_platform_meta FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: video_uploads trg_video_uploads_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_video_uploads_updated_at BEFORE UPDATE ON public.video_uploads FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: videos trg_videos_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_videos_updated_at BEFORE UPDATE ON public.videos FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: weekly_digests trg_weekly_digests_updated_at; Type: TRIGGER; Schema: public; Owner: ongo_user
--

CREATE TRIGGER trg_weekly_digests_updated_at BEFORE UPDATE ON public.weekly_digests FOR EACH ROW EXECUTE FUNCTION public.fn_update_timestamp();


--
-- Name: ab_test_variants ab_test_variants_test_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_test_variants
    ADD CONSTRAINT ab_test_variants_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.ab_tests(id) ON DELETE CASCADE;


--
-- Name: ab_tests ab_tests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_tests
    ADD CONSTRAINT ab_tests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ab_tests ab_tests_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ab_tests
    ADD CONSTRAINT ab_tests_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id);


--
-- Name: activity_logs activity_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.activity_logs
    ADD CONSTRAINT activity_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ai_credit_transactions ai_credit_transactions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credit_transactions
    ADD CONSTRAINT ai_credit_transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: ai_credits ai_credits_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_credits
    ADD CONSTRAINT ai_credits_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: ai_purchased_credits ai_purchased_credits_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ai_purchased_credits
    ADD CONSTRAINT ai_purchased_credits_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: approval_chains approval_chains_approval_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_chains
    ADD CONSTRAINT approval_chains_approval_id_fkey FOREIGN KEY (approval_id) REFERENCES public.approvals(id) ON DELETE CASCADE;


--
-- Name: approval_chains approval_chains_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_chains
    ADD CONSTRAINT approval_chains_approver_id_fkey FOREIGN KEY (approver_id) REFERENCES public.users(id);


--
-- Name: approval_comments approval_comments_approval_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_comments
    ADD CONSTRAINT approval_comments_approval_id_fkey FOREIGN KEY (approval_id) REFERENCES public.approvals(id) ON DELETE CASCADE;


--
-- Name: approval_comments approval_comments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approval_comments
    ADD CONSTRAINT approval_comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: approvals approvals_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id);


--
-- Name: approvals approvals_reviewer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES public.users(id);


--
-- Name: approvals approvals_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: approvals approvals_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.approvals
    ADD CONSTRAINT approvals_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: assets assets_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: automation_rules automation_rules_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.automation_rules
    ADD CONSTRAINT automation_rules_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: automation_workflows automation_workflows_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.automation_workflows
    ADD CONSTRAINT automation_workflows_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: brand_kits brand_kits_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.brand_kits
    ADD CONSTRAINT brand_kits_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: channels channels_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT channels_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: comments comments_parent_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_parent_comment_id_fkey FOREIGN KEY (parent_comment_id) REFERENCES public.comments(id) ON DELETE SET NULL;


--
-- Name: comments comments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: comments comments_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id);


--
-- Name: competitors competitors_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.competitors
    ADD CONSTRAINT competitors_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: content_images content_images_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.content_images
    ADD CONSTRAINT content_images_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: goal_milestones goal_milestones_goal_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.goal_milestones
    ADD CONSTRAINT goal_milestones_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES public.goals(id) ON DELETE CASCADE;


--
-- Name: goals goals_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.goals
    ADD CONSTRAINT goals_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ideas ideas_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.ideas
    ADD CONSTRAINT ideas_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: inbox_messages inbox_messages_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.inbox_messages
    ADD CONSTRAINT inbox_messages_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: inbox_messages inbox_messages_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.inbox_messages
    ADD CONSTRAINT inbox_messages_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id);


--
-- Name: link_bio_links link_bio_links_page_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_links
    ADD CONSTRAINT link_bio_links_page_id_fkey FOREIGN KEY (page_id) REFERENCES public.link_bio_pages(id) ON DELETE CASCADE;


--
-- Name: link_bio_pages link_bio_pages_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.link_bio_pages
    ADD CONSTRAINT link_bio_pages_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: payments payments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: recurring_schedules recurring_schedules_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recurring_schedules
    ADD CONSTRAINT recurring_schedules_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recycling_suggestions recycling_suggestions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recycling_suggestions
    ADD CONSTRAINT recycling_suggestions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recycling_suggestions recycling_suggestions_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.recycling_suggestions
    ADD CONSTRAINT recycling_suggestions_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id);


--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: schedules schedules_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.schedules
    ADD CONSTRAINT schedules_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: schedules schedules_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.schedules
    ADD CONSTRAINT schedules_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: subscriptions subscriptions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: team_members team_members_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.team_members
    ADD CONSTRAINT team_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: templates templates_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.templates
    ADD CONSTRAINT templates_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_settings user_settings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.user_settings
    ADD CONSTRAINT user_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: video_media_info video_media_info_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_media_info
    ADD CONSTRAINT video_media_info_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: video_platform_meta video_platform_meta_video_upload_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_platform_meta
    ADD CONSTRAINT video_platform_meta_video_upload_id_fkey FOREIGN KEY (video_upload_id) REFERENCES public.video_uploads(id) ON DELETE CASCADE;


--
-- Name: video_processing_progress video_processing_progress_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_processing_progress
    ADD CONSTRAINT video_processing_progress_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: video_subtitles video_subtitles_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_subtitles
    ADD CONSTRAINT video_subtitles_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: video_uploads video_uploads_video_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.video_uploads
    ADD CONSTRAINT video_uploads_video_id_fkey FOREIGN KEY (video_id) REFERENCES public.videos(id) ON DELETE CASCADE;


--
-- Name: videos videos_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.videos
    ADD CONSTRAINT videos_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: watermarks watermarks_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.watermarks
    ADD CONSTRAINT watermarks_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: webhooks webhooks_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.webhooks
    ADD CONSTRAINT webhooks_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: weekly_digests weekly_digests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.weekly_digests
    ADD CONSTRAINT weekly_digests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: workflow_actions workflow_actions_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_actions
    ADD CONSTRAINT workflow_actions_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.automation_workflows(id) ON DELETE CASCADE;


--
-- Name: workflow_conditions workflow_conditions_parent_condition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_conditions
    ADD CONSTRAINT workflow_conditions_parent_condition_id_fkey FOREIGN KEY (parent_condition_id) REFERENCES public.workflow_conditions(id) ON DELETE CASCADE;


--
-- Name: workflow_conditions workflow_conditions_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_conditions
    ADD CONSTRAINT workflow_conditions_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.automation_workflows(id) ON DELETE CASCADE;


--
-- Name: workflow_executions workflow_executions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: workflow_executions workflow_executions_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ongo_user
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.automation_workflows(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict Wmy1STn2saAp7mgXuY8fWCJlHvMWKLwianurcqNUIuRol2oyzQUh1rikENTE0p6

