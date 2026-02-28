package com.ongo.infrastructure.persistence.jooq

import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * JDBC 타입 변환 유틸리티.
 * PostgreSQL JDBC 드라이버는 TIMESTAMP → java.sql.Timestamp, DATE → java.sql.Date 를 반환하지만
 * jOOQ Record.get(Field<T>)은 직접 캐스팅하므로 ClassCastException이 발생합니다.
 * 이 함수들은 안전하게 java.time 타입으로 변환합니다.
 */
fun Record.localDateTime(field: Field<LocalDateTime>): LocalDateTime? {
    val value = get(field.name) ?: return null
    return when (value) {
        is LocalDateTime -> value
        is java.sql.Timestamp -> value.toLocalDateTime()
        else -> throw IllegalStateException("Cannot convert ${value::class.java} to LocalDateTime")
    }
}

fun Record.localDate(field: Field<LocalDate>): LocalDate? {
    val value = get(field.name) ?: return null
    return when (value) {
        is LocalDate -> value
        is java.sql.Date -> value.toLocalDate()
        else -> throw IllegalStateException("Cannot convert ${value::class.java} to LocalDate")
    }
}

object Tables {
    val USERS = DSL.table("users")
    val CHANNELS = DSL.table("channels")
    val VIDEOS = DSL.table("videos")
    val VIDEO_UPLOADS = DSL.table("video_uploads")
    val VIDEO_PLATFORM_META = DSL.table("video_platform_meta")
    val SCHEDULES = DSL.table("schedules")
    val ANALYTICS_DAILY = DSL.table("analytics_daily")
    val CHANNEL_INSIGHTS_DAILY = DSL.table("channel_insights_daily")
    val AI_CREDITS = DSL.table("ai_credits")
    val AI_CREDIT_TRANSACTIONS = DSL.table("ai_credit_transactions")
    val AI_PURCHASED_CREDITS = DSL.table("ai_purchased_credits")
    val SUBSCRIPTIONS = DSL.table("subscriptions")
    val PAYMENTS = DSL.table("payments")
    val NOTIFICATIONS = DSL.table("notifications")
    val USER_SETTINGS = DSL.table("user_settings")
    val REFRESH_TOKENS = DSL.table("refresh_tokens")
    val COMPETITORS = DSL.table("competitors")
    val COMPETITOR_ANALYTICS_DAILY = DSL.table("competitor_analytics_daily")
    val AB_TESTS = DSL.table("ab_tests")
    val AB_TEST_VARIANTS = DSL.table("ab_test_variants")
    val COMMENTS = DSL.table("comments")
    val TEMPLATES = DSL.table("templates")
    val ASSETS = DSL.table("assets")
    val WATERMARKS = DSL.table("watermarks")

    // Tools
    val IDEAS = DSL.table("ideas")
    val GOALS = DSL.table("goals")
    val GOAL_MILESTONES = DSL.table("goal_milestones")
    val BRAND_KITS = DSL.table("brand_kits")
    val LINK_BIO_PAGES = DSL.table("link_bio_pages")
    val LINK_BIO_LINKS = DSL.table("link_bio_links")
    val AUTOMATION_RULES = DSL.table("automation_rules")
    val WEBHOOKS = DSL.table("webhooks")
    val RECURRING_SCHEDULES = DSL.table("recurring_schedules")

    // Workspace
    val WORKSPACES = DSL.table("workspaces")

    // Collab
    val TEAM_MEMBERS = DSL.table("team_members")
    val INBOX_MESSAGES = DSL.table("inbox_messages")
    val ACTIVITY_LOGS = DSL.table("activity_logs")
    val RECYCLING_SUGGESTIONS = DSL.table("recycling_suggestions")

    // Video Processing Upgrade
    val VIDEO_MEDIA_INFO = DSL.table("video_media_info")
    val VIDEO_SUBTITLES = DSL.table("video_subtitles")
    val VIDEO_PROCESSING_PROGRESS = DSL.table("video_processing_progress")

    // Automation Workflows
    val AUTOMATION_WORKFLOWS = DSL.table("automation_workflows")
    val WORKFLOW_CONDITIONS = DSL.table("workflow_conditions")
    val WORKFLOW_ACTIONS = DSL.table("workflow_actions")
    val WORKFLOW_EXECUTIONS = DSL.table("workflow_executions")

    // Approval
    val APPROVALS = DSL.table("approvals")
    val APPROVAL_COMMENTS = DSL.table("approval_comments")
    val APPROVAL_CHAINS = DSL.table("approval_chains")

    // AI
    val WEEKLY_DIGESTS = DSL.table("weekly_digests")

    // Content Images (image support)
    val CONTENT_IMAGES = DSL.table("content_images")

    // Video Resizes
    val VIDEO_RESIZES = DSL.table("video_resizes")

    // Trends
    val TRENDS = DSL.table("trends")
    val TREND_ALERTS = DSL.table("trend_alerts")

    // Audience CRM
    val AUDIENCE_PROFILES = DSL.table("audience_profiles")
    val AUDIENCE_SEGMENTS = DSL.table("audience_segments")
    val AUDIENCE_PROFILE_SEGMENTS = DSL.table("audience_profile_segments")

    // Translations
    val VIDEO_TRANSLATIONS = DSL.table("video_translations")

    // Brand Deals
    val BRAND_DEALS = DSL.table("brand_deals")
    val MEDIA_KITS = DSL.table("media_kits")

    // AB Test Results
    val AB_TEST_RESULTS = DSL.table("ab_test_results")
    val TEST_VARIANTS = DSL.table("test_variants")

    // Stub Features (V24)
    val CREATOR_PORTFOLIOS = DSL.table("creator_portfolios")
    val PERFORMANCE_PREDICTIONS = DSL.table("performance_predictions")
    val VISUAL_WORKFLOWS = DSL.table("visual_workflows")
    val BRAND_VOICE_PROFILES = DSL.table("brand_voice_profiles")
    val CREATOR_PROFILES = DSL.table("creator_profiles")
    val COLLABORATION_REQUESTS = DSL.table("collaboration_requests")
    val SENTIMENT_RESULTS = DSL.table("sentiment_results")
    val COMMENT_SENTIMENTS = DSL.table("comment_sentiments")
    val THUMBNAIL_AB_TESTS = DSL.table("thumbnail_ab_tests")
    val FUNNEL_STAGES = DSL.table("funnel_stages")
    val FUNNEL_COMPARISONS = DSL.table("funnel_comparisons")
    val CROSS_PLATFORM_REPORTS = DSL.table("cross_platform_reports")
    val LIBRARY_ITEMS = DSL.table("library_items")
    val LIBRARY_FOLDERS = DSL.table("library_folders")
    val REVENUE_GOALS = DSL.table("revenue_goals")
    val REVENUE_GOAL_MILESTONES = DSL.table("revenue_goal_milestones")
    val LIVE_STREAMS = DSL.table("live_streams")
    val STREAM_CHATS = DSL.table("stream_chats")
    val CONTENT_AB_TESTS = DSL.table("content_ab_tests")
    val CONTENT_VARIANTS = DSL.table("content_variants")
    val FAN_CAMPAIGNS = DSL.table("fan_campaigns")
    val CAMPAIGN_SEGMENTS = DSL.table("campaign_segments")
    val CONTENT_RIGHTS = DSL.table("content_rights")
    val RIGHTS_ALERTS = DSL.table("rights_alerts")
    val CONTENT_CLIPS = DSL.table("content_clips")
    val VIDEO_CAPTIONS = DSL.table("video_captions")
    val AI_THUMBNAILS = DSL.table("ai_thumbnails")

    // Live Dashboard
    val LIVE_DASHBOARD_ALERTS = DSL.table("live_dashboard_alerts")
    val LIVE_ALERT_CONFIGS = DSL.table("live_alert_configs")

    // Pricing V2
    val WEBHOOK_EVENTS = DSL.table("webhook_events")
    val COUPONS = DSL.table("coupons")
    val COUPON_USAGES = DSL.table("coupon_usages")
    val USAGE_ALERT_CONFIGS = DSL.table("usage_alert_configs")
}

object Fields {
    // Common
    val ID = DSL.field("id", Long::class.java)
    val USER_ID = DSL.field("user_id", Long::class.java)
    val CREATED_AT = DSL.field("created_at", java.time.LocalDateTime::class.java)
    val UPDATED_AT = DSL.field("updated_at", java.time.LocalDateTime::class.java)

    // users
    val EMAIL = DSL.field("email", String::class.java)
    val NAME = DSL.field("name", String::class.java)
    val NICKNAME = DSL.field("nickname", String::class.java)
    val PROFILE_IMAGE_URL = DSL.field("profile_image_url", String::class.java)
    val PROVIDER = DSL.field("provider", String::class.java)
    val PROVIDER_TEXT = DSL.field("provider::text", String::class.java)
    val PROVIDER_ID = DSL.field("provider_id", String::class.java)
    val PLAN_TYPE = DSL.field("plan_type", String::class.java)
    val CATEGORY = DSL.field("category", String::class.java)
    val ONBOARDING_COMPLETED = DSL.field("onboarding_completed", Boolean::class.java)
    val ROLE = DSL.field("role", String::class.java)

    // channels
    val PLATFORM = DSL.field("platform", String::class.java)
    val PLATFORM_TEXT = DSL.field("platform::text", String::class.java)
    val PLATFORM_CHANNEL_ID = DSL.field("platform_channel_id", String::class.java)
    val CHANNEL_NAME = DSL.field("channel_name", String::class.java)
    val CHANNEL_URL = DSL.field("channel_url", String::class.java)
    val SUBSCRIBER_COUNT = DSL.field("subscriber_count", Long::class.java)
    val ACCESS_TOKEN = DSL.field("access_token", String::class.java)
    val REFRESH_TOKEN = DSL.field("refresh_token", String::class.java)
    val TOKEN_EXPIRES_AT = DSL.field("token_expires_at", java.time.LocalDateTime::class.java)
    val STATUS = DSL.field("status", String::class.java)
    /** PostgreSQL enum 비교용: status::text = ? */
    val STATUS_TEXT = DSL.field("status::text", String::class.java)
    val CONNECTED_AT = DSL.field("connected_at", java.time.LocalDateTime::class.java)

    // videos
    val TITLE = DSL.field("title", String::class.java)
    val DESCRIPTION = DSL.field("description", String::class.java)
    val TAGS = DSL.field("tags", Array<String>::class.java)
    val FILE_URL = DSL.field("file_url", String::class.java)
    val FILE_SIZE_BYTES = DSL.field("file_size_bytes", Long::class.java)
    val DURATION_SECONDS = DSL.field("duration_seconds", Int::class.java)
    val RESOLUTION = DSL.field("resolution", String::class.java)
    val ORIGINAL_FILENAME = DSL.field("original_filename", String::class.java)
    val CONTENT_HASH = DSL.field("content_hash", String::class.java)
    val THUMBNAIL_URLS = DSL.field("thumbnail_urls", Any::class.java)

    // video_uploads
    val VIDEO_ID = DSL.field("video_id", Long::class.java)
    val PLATFORM_VIDEO_ID = DSL.field("platform_video_id", String::class.java)
    val ERROR_MESSAGE = DSL.field("error_message", String::class.java)
    val PLATFORM_URL = DSL.field("platform_url", String::class.java)
    val PUBLISHED_AT = DSL.field("published_at", java.time.LocalDateTime::class.java)

    // video_platform_meta
    val VIDEO_UPLOAD_ID = DSL.field("video_upload_id", Long::class.java)
    val VISIBILITY = DSL.field("visibility", String::class.java)
    val CUSTOM_THUMBNAIL_URL = DSL.field("custom_thumbnail_url", String::class.java)

    // schedules
    val SCHEDULED_AT = DSL.field("scheduled_at", java.time.LocalDateTime::class.java)
    val PLATFORMS = DSL.field("platforms", Any::class.java)

    // analytics_daily
    val DATE = DSL.field("date", java.time.LocalDate::class.java)
    val VIEWS = DSL.field("views", Int::class.java)
    val LIKES = DSL.field("likes", Int::class.java)
    val COMMENTS_COUNT = DSL.field("comments_count", Int::class.java)
    val SHARES = DSL.field("shares", Int::class.java)
    val WATCH_TIME_SECONDS = DSL.field("watch_time_seconds", Long::class.java)
    val SUBSCRIBER_GAINED = DSL.field("subscriber_gained", Int::class.java)
    val REVENUE_MICRO = DSL.field("revenue_micro", Long::class.java)

    // analytics_daily extended
    val IMPRESSIONS = DSL.field("impressions", Int::class.java)
    val AVG_VIEW_DURATION_SECONDS = DSL.field("avg_view_duration_seconds", Int::class.java)

    // channel_insights_daily
    val TRAFFIC_SOURCE = DSL.field("traffic_source", Any::class.java)
    val DEMOGRAPHICS_AGE = DSL.field("demographics_age", Any::class.java)
    val DEMOGRAPHICS_GENDER = DSL.field("demographics_gender", Any::class.java)
    val DEMOGRAPHICS_COUNTRY = DSL.field("demographics_country", Any::class.java)

    // ai_credits
    val BALANCE = DSL.field("balance", Int::class.java)
    val FREE_MONTHLY = DSL.field("free_monthly", Int::class.java)
    val FREE_REMAINING = DSL.field("free_remaining", Int::class.java)
    val FREE_RESET_DATE = DSL.field("free_reset_date", java.time.LocalDate::class.java)

    // ai_credit_transactions
    val TYPE = DSL.field("type", String::class.java)
    val AMOUNT = DSL.field("amount", Int::class.java)
    val BALANCE_AFTER = DSL.field("balance_after", Int::class.java)
    val FEATURE = DSL.field("feature", String::class.java)
    val REFERENCE_ID = DSL.field("reference_id", Long::class.java)

    // ai_purchased_credits
    val PACKAGE_NAME = DSL.field("package_name", String::class.java)
    val TOTAL_CREDITS = DSL.field("total_credits", Int::class.java)
    val REMAINING = DSL.field("remaining", Int::class.java)
    val PRICE = DSL.field("price", Int::class.java)
    val PURCHASED_AT = DSL.field("purchased_at", java.time.LocalDateTime::class.java)
    val EXPIRES_AT = DSL.field("expires_at", java.time.LocalDateTime::class.java)

    // subscriptions
    val BILLING_CYCLE = DSL.field("billing_cycle", String::class.java)
    val CURRENT_PERIOD_START = DSL.field("current_period_start", java.time.LocalDateTime::class.java)
    val CURRENT_PERIOD_END = DSL.field("current_period_end", java.time.LocalDateTime::class.java)
    val NEXT_BILLING_DATE = DSL.field("next_billing_date", java.time.LocalDateTime::class.java)
    val STORAGE_QUOTA_LIMIT_BYTES = DSL.field("storage_quota_limit_bytes", Long::class.javaObjectType)
    val CANCELLED_AT = DSL.field("cancelled_at", java.time.LocalDateTime::class.java)
    val PENDING_PLAN_TYPE = DSL.field("pending_plan_type", String::class.java)
    val PADDLE_SUBSCRIPTION_ID = DSL.field("paddle_subscription_id", String::class.java)
    val PADDLE_CUSTOMER_ID = DSL.field("paddle_customer_id", String::class.java)

    // payments
    val CURRENCY = DSL.field("currency", String::class.java)
    val PG_PROVIDER = DSL.field("pg_provider", String::class.java)
    val PG_TRANSACTION_ID = DSL.field("pg_transaction_id", String::class.java)
    val PAYMENT_METHOD = DSL.field("payment_method", String::class.java)
    val RECEIPT_URL = DSL.field("receipt_url", String::class.java)
    val PADDLE_TRANSACTION_ID = DSL.field("paddle_transaction_id", String::class.java)
    val PADDLE_INVOICE_URL = DSL.field("paddle_invoice_url", String::class.java)

    // notifications
    val MESSAGE = DSL.field("message", String::class.java)
    val IS_READ = DSL.field("is_read", Boolean::class.java)
    val REFERENCE_TYPE = DSL.field("reference_type", String::class.java)

    // user_settings
    val DEFAULT_VISIBILITY = DSL.field("default_visibility", String::class.java)
    val DEFAULT_PLATFORMS = DSL.field("default_platforms", Any::class.java)
    val DEFAULT_AI_TONE = DSL.field("default_ai_tone", String::class.java)
    val DEFAULT_AI_PROVIDER = DSL.field("default_ai_provider", String::class.java)
    val NOTIFICATION_UPLOAD = DSL.field("notification_upload", Boolean::class.java)
    val NOTIFICATION_COMMENT = DSL.field("notification_comment", String::class.java)
    val NOTIFICATION_CREDIT_THRESHOLD = DSL.field("notification_credit_threshold", Int::class.java)
    val NOTIFICATION_SCHEDULE_REMINDER = DSL.field("notification_schedule_reminder", Int::class.java)

    // refresh_tokens
    val TOKEN = DSL.field("token", String::class.java)

    // competitors
    val TOTAL_VIEWS = DSL.field("total_views", Long::class.java)
    val VIDEO_COUNT = DSL.field("video_count", Int::class.java)
    val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
    val LAST_SYNCED_AT = DSL.field("last_synced_at", java.time.LocalDateTime::class.java)

    // competitor_analytics_daily
    val COMPETITOR_ID = DSL.field("competitor_id", Long::class.java)
    val AVG_LIKES = DSL.field("avg_likes", Long::class.java)
    val AVG_COMMENTS = DSL.field("avg_comments", Long::class.java)

    // ab_tests
    val TEST_NAME = DSL.field("test_name", String::class.java)
    val METRIC_TYPE = DSL.field("metric_type", String::class.java)
    val WINNER_VARIANT_ID = DSL.field("winner_variant_id", Long::class.java)
    val STARTED_AT = DSL.field("started_at", java.time.LocalDateTime::class.java)
    val ENDED_AT = DSL.field("ended_at", java.time.LocalDateTime::class.java)

    // ab_test_variants
    val TEST_ID = DSL.field("test_id", Long::class.java)
    val VARIANT_NAME = DSL.field("variant_name", String::class.java)
    val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
    val CLICKS = DSL.field("clicks", Long::class.java)
    val ENGAGEMENT_RATE = DSL.field("engagement_rate", java.math.BigDecimal::class.java)

    // comments
    val PLATFORM_COMMENT_ID = DSL.field("platform_comment_id", String::class.java)
    val AUTHOR_NAME = DSL.field("author_name", String::class.java)
    val AUTHOR_AVATAR_URL = DSL.field("author_avatar_url", String::class.java)
    val AUTHOR_CHANNEL_URL = DSL.field("author_channel_url", String::class.java)
    val CONTENT = DSL.field("content", String::class.java)
    val SENTIMENT = DSL.field("sentiment", String::class.java)
    val LIKE_COUNT = DSL.field("like_count", Int::class.java)
    val REPLY_COUNT = DSL.field("reply_count", Int::class.java)
    val PARENT_COMMENT_ID = DSL.field("parent_comment_id", Long::class.java)
    val PLATFORM_REPLY_ID = DSL.field("platform_reply_id", String::class.java)
    val PLATFORM_LIKE_ID = DSL.field("platform_like_id", String::class.java)
    val IS_REPLIED = DSL.field("is_replied", Boolean::class.java)
    val IS_HIDDEN = DSL.field("is_hidden", Boolean::class.java)
    val IS_PINNED = DSL.field("is_pinned", Boolean::class.java)
    val REPLY_CONTENT = DSL.field("reply_content", String::class.java)
    val REPLIED_AT = DSL.field("replied_at", java.time.LocalDateTime::class.java)
    val SYNCED_AT = DSL.field("synced_at", java.time.LocalDateTime::class.java)

    // templates
    val TITLE_TEMPLATE = DSL.field("title_template", String::class.java)
    val DESCRIPTION_TEMPLATE = DSL.field("description_template", String::class.java)
    val USAGE_COUNT = DSL.field("usage_count", Int::class.java)

    // assets
    val FILENAME = DSL.field("filename", String::class.java)
    val FILE_TYPE = DSL.field("file_type", String::class.java)
    val MIME_TYPE = DSL.field("mime_type", String::class.java)
    val FOLDER = DSL.field("folder", String::class.java)
    val WIDTH = DSL.field("width", Int::class.java)
    val HEIGHT = DSL.field("height", Int::class.java)

    // watermarks
    val IMAGE_URL = DSL.field("image_url", String::class.java)
    val POSITION = DSL.field("position", String::class.java)
    val OPACITY = DSL.field("opacity", java.math.BigDecimal::class.java)
    val SIZE = DSL.field("size", Int::class.java)
    val IS_DEFAULT = DSL.field("is_default", Boolean::class.java)

    // ideas
    val PRIORITY = DSL.field("priority", String::class.java)
    val SOURCE = DSL.field("source", String::class.java)
    val REFERENCE_URL = DSL.field("reference_url", String::class.java)
    val DUE_DATE = DSL.field("due_date", java.time.LocalDate::class.java)

    // goals
    val TARGET_VALUE = DSL.field("target_value", Long::class.java)
    val CURRENT_VALUE = DSL.field("current_value", Long::class.java)
    val START_DATE = DSL.field("start_date", java.time.LocalDate::class.java)
    val END_DATE = DSL.field("end_date", java.time.LocalDate::class.java)

    // goal_milestones
    val GOAL_ID = DSL.field("goal_id", Long::class.java)
    val IS_REACHED = DSL.field("is_reached", Boolean::class.java)
    val REACHED_AT = DSL.field("reached_at", java.time.LocalDateTime::class.java)

    // brand_kits
    val PRIMARY_COLOR = DSL.field("primary_color", String::class.java)
    val SECONDARY_COLOR = DSL.field("secondary_color", String::class.java)
    val ACCENT_COLOR = DSL.field("accent_color", String::class.java)
    val FONT_FAMILY = DSL.field("font_family", String::class.java)
    val LOGO_URL = DSL.field("logo_url", String::class.java)
    val INTRO_TEMPLATE_URL = DSL.field("intro_template_url", String::class.java)
    val OUTRO_TEMPLATE_URL = DSL.field("outro_template_url", String::class.java)
    val WATERMARK_URL = DSL.field("watermark_url", String::class.java)
    val GUIDELINES = DSL.field("guidelines", String::class.java)

    // link_bio_pages
    val SLUG = DSL.field("slug", String::class.java)
    val BIO = DSL.field("bio", String::class.java)
    val AVATAR_URL = DSL.field("avatar_url", String::class.java)
    val THEME = DSL.field("theme", String::class.java)
    val BACKGROUND_COLOR = DSL.field("background_color", String::class.java)
    val TEXT_COLOR = DSL.field("text_color", String::class.java)
    val IS_PUBLISHED = DSL.field("is_published", Boolean::class.java)
    val VIEW_COUNT = DSL.field("view_count", Long::class.java)

    // link_bio_links
    val PAGE_ID = DSL.field("page_id", Long::class.java)
    val URL = DSL.field("url", String::class.java)
    val ICON = DSL.field("icon", String::class.java)
    val SORT_ORDER = DSL.field("sort_order", Int::class.java)
    val CLICK_COUNT = DSL.field("click_count", Long::class.java)
    val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)

    // automation_rules
    val TRIGGER_TYPE = DSL.field("trigger_type", String::class.java)
    val TRIGGER_CONFIG = DSL.field("trigger_config", Any::class.java)
    val ACTION_TYPE = DSL.field("action_type", String::class.java)
    val ACTION_CONFIG = DSL.field("action_config", Any::class.java)
    val LAST_TRIGGERED_AT = DSL.field("last_triggered_at", java.time.LocalDateTime::class.java)
    val EXECUTION_COUNT = DSL.field("execution_count", Int::class.java)

    // webhooks
    val EVENTS = DSL.field("events", Array<String>::class.java)
    val SECRET = DSL.field("secret", String::class.java)
    val LAST_STATUS_CODE = DSL.field("last_status_code", Int::class.java)
    val FAILURE_COUNT = DSL.field("failure_count", Int::class.java)

    // recurring_schedules
    val FREQUENCY = DSL.field("frequency", String::class.java)
    val DAY_OF_WEEK = DSL.field("day_of_week", Int::class.java)
    val DAY_OF_MONTH = DSL.field("day_of_month", Int::class.java)
    val TIME_OF_DAY = DSL.field("time_of_day", java.time.LocalTime::class.java)
    val TIMEZONE = DSL.field("timezone", String::class.java)
    val NEXT_RUN_AT = DSL.field("next_run_at", java.time.LocalDateTime::class.java)
    val LAST_RUN_AT = DSL.field("last_run_at", java.time.LocalDateTime::class.java)

    // workspaces
    val OWNER_ID = DSL.field("owner_id", Long::class.java)
    val WORKSPACE_NAME = DSL.field("name", String::class.java)
    val WORKSPACE_SLUG = DSL.field("slug", String::class.java)
    val WORKSPACE_DESCRIPTION = DSL.field("description", String::class.java)
    val WORKSPACE_LOGO_URL = DSL.field("logo_url", String::class.java)

    // team_members extended
    val WORKSPACE_ID = DSL.field("workspace_id", Long::class.java)

    // team_members
    val MEMBER_EMAIL = DSL.field("member_email", String::class.java)
    val MEMBER_NAME = DSL.field("member_name", String::class.java)
    val PERMISSIONS = DSL.field("permissions", String::class.java)
    val INVITED_AT = DSL.field("invited_at", java.time.LocalDateTime::class.java)
    val JOINED_AT = DSL.field("joined_at", java.time.LocalDateTime::class.java)

    // inbox_messages
    val SENDER_NAME = DSL.field("sender_name", String::class.java)
    val SENDER_AVATAR_URL = DSL.field("sender_avatar_url", String::class.java)
    val MESSAGE_TYPE = DSL.field("message_type", String::class.java)
    val IS_STARRED = DSL.field("is_starred", Boolean::class.java)
    val PLATFORM_MESSAGE_ID = DSL.field("platform_message_id", String::class.java)
    val RECEIVED_AT = DSL.field("received_at", java.time.LocalDateTime::class.java)

    // video_media_info shared fields
    val BITRATE_KBPS = DSL.field("bitrate_kbps", Int::class.java)
    val FPS = DSL.field("fps", Double::class.java)

    // video_media_info
    val VIDEO_CODEC = DSL.field("video_codec", String::class.java)
    val DURATION_MS = DSL.field("duration_ms", Long::class.java)
    val COLOR_SPACE = DSL.field("color_space", String::class.java)
    val PIXEL_FORMAT = DSL.field("pixel_format", String::class.java)
    val PROFILE = DSL.field("profile", String::class.java)
    val AUDIO_CODEC = DSL.field("audio_codec", String::class.java)
    val AUDIO_BITRATE_KBPS = DSL.field("audio_bitrate_kbps", Int::class.java)
    val SAMPLE_RATE = DSL.field("sample_rate", Int::class.java)
    val AUDIO_CHANNELS = DSL.field("audio_channels", Int::class.java)
    val FORMAT_NAME = DSL.field("format_name", String::class.java)
    val RAW_JSON = DSL.field("raw_json", Any::class.java)

    // video_subtitles
    val LANGUAGE = DSL.field("language", String::class.java)
    val FORMAT = DSL.field("format", String::class.java)
    val IS_AUTO_GENERATED = DSL.field("is_auto_generated", Boolean::class.java)

    // video_processing_progress
    val STAGE = DSL.field("stage", String::class.java)
    val PROGRESS_PERCENT = DSL.field("progress_percent", Int::class.java)

    // videos extended
    val AUTO_THUMBNAILS = DSL.field("auto_thumbnails", Any::class.java)
    val SELECTED_THUMBNAIL_INDEX = DSL.field("selected_thumbnail_index", Int::class.java)
    val MEDIA_TYPE = DSL.field("media_type", String::class.java)
    val MEDIA_TYPE_TEXT = DSL.field("media_type::text", String::class.java)

    // content_images
    val IMAGE_URL_CI = DSL.field("image_url", String::class.java)
    val DISPLAY_ORDER = DSL.field("display_order", Int::class.java)
    val CONTENT_TYPE = DSL.field("content_type", String::class.java)

    // automation_workflows
    val ENABLED = DSL.field("enabled", Boolean::class.java)

    // workflow_conditions
    val WORKFLOW_ID = DSL.field("workflow_id", Long::class.java)
    val PARENT_CONDITION_ID = DSL.field("parent_condition_id", Long::class.java)
    val GROUP_TYPE = DSL.field("group_type", String::class.java)
    val FIELD = DSL.field("field", String::class.java)
    val OPERATOR = DSL.field("operator", String::class.java)
    val VALUE = DSL.field("value", String::class.java)
    val EXPRESSION = DSL.field("expression", String::class.java)

    // workflow_actions
    val DELAY_MINUTES = DSL.field("delay_minutes", Int::class.java)
    val CONFIG_JSONB = DSL.field("config", Any::class.java)

    // workflow_executions
    val TRIGGER_DATA = DSL.field("trigger_data", Any::class.java)
    val ACTION_RESULTS = DSL.field("action_results", Any::class.java)
    val STARTED_AT_EXEC = DSL.field("started_at", java.time.LocalDateTime::class.java)
    val COMPLETED_AT = DSL.field("completed_at", java.time.LocalDateTime::class.java)

    // activity_logs
    val ACTION = DSL.field("action", String::class.java)
    val ENTITY_TYPE = DSL.field("entity_type", String::class.java)
    val ENTITY_ID = DSL.field("entity_id", Long::class.java)
    val DETAILS = DSL.field("details", String::class.java)
    val IP_ADDRESS = DSL.field("ip_address", String::class.java)
    val USER_AGENT = DSL.field("user_agent", String::class.java)

    // approvals
    val VIDEO_TITLE = DSL.field("video_title", String::class.java)
    val REQUESTER_ID = DSL.field("requester_id", Long::class.java)
    val REQUESTER_NAME = DSL.field("requester_name", String::class.java)
    val REVIEWER_ID = DSL.field("reviewer_id", Long::class.java)
    val REVIEWER_NAME = DSL.field("reviewer_name", String::class.java)
    val COMMENT = DSL.field("comment", String::class.java)
    val REVISION_NOTE = DSL.field("revision_note", String::class.java)
    val REQUESTED_AT = DSL.field("requested_at", java.time.LocalDateTime::class.java)
    val DECIDED_AT = DSL.field("decided_at", java.time.LocalDateTime::class.java)

    // approval_comments
    val APPROVAL_ID = DSL.field("approval_id", Long::class.java)
    val USER_NAME = DSL.field("user_name", String::class.java)

    // approval_chains
    val STEP_ORDER = DSL.field("step_order", Int::class.java)
    val APPROVER_ID = DSL.field("approver_id", Long::class.java)
    val APPROVER_NAME = DSL.field("approver_name", String::class.java)
    val DEADLINE_AT = DSL.field("deadline_at", java.time.LocalDateTime::class.java)
    val APPROVED_AT = DSL.field("approved_at", java.time.LocalDateTime::class.java)

    // weekly_digests
    val WEEK_START_DATE = DSL.field("week_start_date", java.time.LocalDate::class.java)
    val WEEK_END_DATE = DSL.field("week_end_date", java.time.LocalDate::class.java)
    val SUMMARY = DSL.field("summary", String::class.java)
    val TOP_VIDEOS = DSL.field("top_videos", String::class.java)
    val ANOMALIES = DSL.field("anomalies", String::class.java)
    val ACTION_ITEMS = DSL.field("action_items", String::class.java)
    val GENERATED_AT = DSL.field("generated_at", java.time.LocalDateTime::class.java)
    val PLATFORMS_STR = DSL.field("platforms", String::class.java)

    // video_resizes
    val ORIGINAL_VIDEO_ID = DSL.field("original_video_id", Long::class.java)
    val ASPECT_RATIO = DSL.field("aspect_ratio", String::class.java)
    val COMPLETED_AT_RESIZE = DSL.field("completed_at", java.time.LocalDateTime::class.java)

    // trends
    val KEYWORD = DSL.field("keyword", String::class.java)
    val SCORE = DSL.field("score", Double::class.java)
    val REGION = DSL.field("region", String::class.java)
    val METADATA = DSL.field("metadata", String::class.java)

    // trend_alerts
    val THRESHOLD = DSL.field("threshold", Double::class.java)

    // audience_profiles
    val ENGAGEMENT_SCORE = DSL.field("engagement_score", Double::class.java)
    val TOTAL_INTERACTIONS = DSL.field("total_interactions", Int::class.java)
    val POSITIVE_RATIO = DSL.field("positive_ratio", Double::class.java)
    val FIRST_SEEN_AT = DSL.field("first_seen_at", java.time.LocalDateTime::class.java)
    val LAST_SEEN_AT = DSL.field("last_seen_at", java.time.LocalDateTime::class.java)
    val TAGS_JSONB = DSL.field("tags", String::class.java)

    // audience_segments
    val CONDITIONS = DSL.field("conditions", String::class.java)
    val AUTO_UPDATE = DSL.field("auto_update", Boolean::class.java)
    val MEMBER_COUNT = DSL.field("member_count", Int::class.java)

    // audience_profile_segments
    val PROFILE_ID = DSL.field("profile_id", Long::class.java)
    val SEGMENT_ID = DSL.field("segment_id", Long::class.java)

    // video_translations
    val SUBTITLE_CONTENT = DSL.field("subtitle_content", String::class.java)

    // brand_deals
    val BRAND_NAME = DSL.field("brand_name", String::class.java)
    val CONTACT_NAME = DSL.field("contact_name", String::class.java)
    val CONTACT_EMAIL = DSL.field("contact_email", String::class.java)
    val DEAL_VALUE = DSL.field("deal_value", Long::class.java)
    val DEADLINE_BD = DSL.field("deadline", java.time.LocalDate::class.java)
    val DELIVERABLES = DSL.field("deliverables", String::class.java)
    val NOTES_BD = DSL.field("notes", String::class.java)

    // media_kits
    val DISPLAY_NAME = DSL.field("display_name", String::class.java)
    val CATEGORIES_MK = DSL.field("categories", String::class.java)
    val SOCIAL_LINKS = DSL.field("social_links", String::class.java)
    val STATS_SNAPSHOT = DSL.field("stats_snapshot", String::class.java)
    val RATE_CARD = DSL.field("rate_card", String::class.java)
    val IS_PUBLIC_MK = DSL.field("is_public", Boolean::class.java)

    // ab_test_results
    val CONFIDENCE = DSL.field("confidence", java.math.BigDecimal::class.java)
    val METRIC = DSL.field("metric", String::class.java)
    val WINNER = DSL.field("winner", String::class.java)

    // test_variants
    val RESULT_ID = DSL.field("result_id", Long::class.java)
    val VARIANT_ID = DSL.field("variant_id", String::class.java)
    val CTR = DSL.field("ctr", java.math.BigDecimal::class.java)
    val AVG_WATCH_TIME = DSL.field("avg_watch_time", Int::class.java)
    val ENGAGEMENT = DSL.field("engagement", java.math.BigDecimal::class.java)
    val CONVERSIONS = DSL.field("conversions", Int::class.java)
    val IS_CONTROL = DSL.field("is_control", Boolean::class.java)
    val IS_WINNER = DSL.field("is_winner", Boolean::class.java)

    // =====================================================
    // Stub Features Fields (V24)
    // =====================================================

    // creator_portfolios
    val PUBLIC_SLUG = DSL.field("public_slug", String::class.java)
    val SHOWCASE_VIDEOS = DSL.field("showcase_videos", String::class.java)
    val BRAND_HISTORY = DSL.field("brand_history", String::class.java)
    val IS_PUBLIC = DSL.field("is_public", Boolean::class.java)

    // performance_predictions
    val PREDICTED_VIEWS = DSL.field("predicted_views", Long::class.java)
    val PREDICTED_LIKES = DSL.field("predicted_likes", Long::class.java)
    val PREDICTED_ENGAGEMENT_RATE = DSL.field("predicted_engagement_rate", java.math.BigDecimal::class.java)
    val CONFIDENCE_SCORE = DSL.field("confidence_score", java.math.BigDecimal::class.java)
    val OPTIMAL_UPLOAD_TIME = DSL.field("optimal_upload_time", java.time.LocalDateTime::class.java)
    val PREDICTION_DATA = DSL.field("prediction_data", String::class.java)
    val ACTUAL_VIEWS = DSL.field("actual_views", Long::class.java)
    val ACTUAL_LIKES = DSL.field("actual_likes", Long::class.java)

    // visual_workflows
    val WORKFLOW_DATA = DSL.field("workflow_data", String::class.java)
    val TRIGGER_COUNT = DSL.field("trigger_count", Int::class.java)

    // brand_voice_profiles
    val TONE = DSL.field("tone", String::class.java)
    val TRAIN_STATUS = DSL.field("train_status", String::class.java)
    val SAMPLE_COUNT = DSL.field("sample_count", Int::class.java)
    val VOCABULARY = DSL.field("vocabulary", String::class.java)
    val AVOID_WORDS = DSL.field("avoid_words", String::class.java)
    val EMOJI_USAGE = DSL.field("emoji_usage", String::class.java)
    val AVG_SENTENCE_LENGTH = DSL.field("avg_sentence_length", Int::class.java)
    val SIGNATURE_PHRASE = DSL.field("signature_phrase", String::class.java)

    // creator_profiles
    val SUBSCRIBERS = DSL.field("subscribers", Long::class.java)
    val MATCH_SCORE = DSL.field("match_score", Int::class.java)
    val IS_CONNECTED = DSL.field("is_connected", Boolean::class.java)

    // collaboration_requests
    val FROM_CREATOR_ID = DSL.field("from_creator_id", Long::class.java)
    val TO_CREATOR_ID = DSL.field("to_creator_id", Long::class.java)
    val PROPOSED_TYPE = DSL.field("proposed_type", String::class.java)

    // sentiment_results
    val CONTENT_TITLE = DSL.field("content_title", String::class.java)
    val TOTAL_COMMENTS = DSL.field("total_comments", Int::class.java)
    val POSITIVE_COUNT = DSL.field("positive_count", Int::class.java)
    val NEUTRAL_COUNT = DSL.field("neutral_count", Int::class.java)
    val NEGATIVE_COUNT = DSL.field("negative_count", Int::class.java)
    val POSITIVE_RATE = DSL.field("positive_rate", java.math.BigDecimal::class.java)
    val AVG_SENTIMENT_SCORE = DSL.field("avg_sentiment_score", Int::class.java)
    val TOP_POSITIVE_KEYWORDS = DSL.field("top_positive_keywords", String::class.java)
    val TOP_NEGATIVE_KEYWORDS = DSL.field("top_negative_keywords", String::class.java)
    val ANALYZED_AT = DSL.field("analyzed_at", java.time.LocalDateTime::class.java)

    // comment_sentiments
    val COMMENT_TEXT = DSL.field("comment_text", String::class.java)
    val KEYWORDS = DSL.field("keywords", String::class.java)

    // thumbnail_ab_tests
    val VARIANT_A_IMAGE_URL = DSL.field("variant_a_image_url", String::class.java)
    val VARIANT_A_CTR = DSL.field("variant_a_ctr", java.math.BigDecimal::class.java)
    val VARIANT_A_IMPRESSIONS = DSL.field("variant_a_impressions", Long::class.java)
    val VARIANT_A_CLICKS = DSL.field("variant_a_clicks", Long::class.java)
    val VARIANT_B_IMAGE_URL = DSL.field("variant_b_image_url", String::class.java)
    val VARIANT_B_CTR = DSL.field("variant_b_ctr", java.math.BigDecimal::class.java)
    val VARIANT_B_IMPRESSIONS = DSL.field("variant_b_impressions", Long::class.java)
    val VARIANT_B_CLICKS = DSL.field("variant_b_clicks", Long::class.java)

    // funnel_stages
    val COUNT = DSL.field("count", Long::class.java)
    val RATE = DSL.field("rate", java.math.BigDecimal::class.java)
    val DROP_OFF = DSL.field("drop_off", java.math.BigDecimal::class.java)
    val MEASURED_AT = DSL.field("measured_at", java.time.LocalDateTime::class.java)

    // funnel_comparisons
    val VIDEO_ID_A = DSL.field("video_id_a", Long::class.java)
    val VIDEO_TITLE_A = DSL.field("video_title_a", String::class.java)
    val VIDEO_ID_B = DSL.field("video_id_b", Long::class.java)
    val VIDEO_TITLE_B = DSL.field("video_title_b", String::class.java)

    // cross_platform_reports
    val PERIOD = DSL.field("period", String::class.java)
    val CONTENTS = DSL.field("contents", String::class.java)
    val PLATFORM_SUMMARIES = DSL.field("platform_summaries", String::class.java)
    val AUDIENCE_OVERLAP = DSL.field("audience_overlap", String::class.java)
    val RECOMMENDATIONS = DSL.field("recommendations", String::class.java)

    // library_items
    val FILE_SIZE = DSL.field("file_size", Long::class.java)
    val FOLDER_ID = DSL.field("folder_id", Long::class.java)
    val UPLOADED_AT = DSL.field("uploaded_at", java.time.LocalDateTime::class.java)

    // library_folders
    val PARENT_ID = DSL.field("parent_id", Long::class.java)
    val COLOR = DSL.field("color", String::class.java)

    // revenue_goals
    val TARGET_AMOUNT = DSL.field("target_amount", Long::class.java)
    val CURRENT_AMOUNT = DSL.field("current_amount", Long::class.java)
    val PROGRESS = DSL.field("progress", Int::class.java)

    // revenue_goal_milestones
    val LABEL = DSL.field("label", String::class.java)
    val REACHED = DSL.field("reached", Boolean::class.java)

    // live_streams
    val VIEWER_COUNT = DSL.field("viewer_count", Int::class.java)
    val PEAK_VIEWERS = DSL.field("peak_viewers", Int::class.java)
    val CHAT_MESSAGES = DSL.field("chat_messages", Int::class.java)
    val STREAM_URL = DSL.field("stream_url", String::class.java)

    // stream_chats
    val STREAM_ID = DSL.field("stream_id", Long::class.java)
    val USERNAME = DSL.field("username", String::class.java)
    val TIMESTAMP = DSL.field("timestamp", java.time.LocalDateTime::class.java)
    val IS_HIGHLIGHTED = DSL.field("is_highlighted", Boolean::class.java)
    val IS_MODERATOR = DSL.field("is_moderator", Boolean::class.java)

    // content_variants
    val COMMENTS_LONG = DSL.field("comments", Long::class.java)

    // fan_campaigns
    val SEGMENT_ID_FK = DSL.field("segment_id", Long::class.java)
    val SEGMENT_NAME = DSL.field("segment_name", String::class.java)
    val CAMPAIGN_TYPE = DSL.field("campaign_type", String::class.java)
    val TARGET_COUNT = DSL.field("target_count", Int::class.java)
    val SENT_COUNT = DSL.field("sent_count", Int::class.java)
    val OPEN_RATE = DSL.field("open_rate", java.math.BigDecimal::class.java)
    val CLICK_RATE = DSL.field("click_rate", java.math.BigDecimal::class.java)

    // campaign_segments
    val CRITERIA = DSL.field("criteria", String::class.java)
    val FAN_COUNT = DSL.field("fan_count", Int::class.java)
    val AVG_ENGAGEMENT = DSL.field("avg_engagement", java.math.BigDecimal::class.java)

    // content_rights
    val ASSET_NAME = DSL.field("asset_name", String::class.java)
    val ASSET_TYPE = DSL.field("asset_type", String::class.java)
    val LICENSE_TYPE = DSL.field("license_type", String::class.java)
    val LICENSE_STATUS = DSL.field("license_status", String::class.java)
    val LICENSE_URL = DSL.field("license_url", String::class.java)
    val PURCHASED_AT_CR = DSL.field("purchased_at", java.time.LocalDateTime::class.java)
    val COST = DSL.field("cost", Long::class.java)
    val NOTES = DSL.field("notes", String::class.java)

    // rights_alerts
    val CONTENT_RIGHT_ID = DSL.field("content_right_id", Long::class.java)
    val SEVERITY = DSL.field("severity", String::class.java)
    val DAYS_UNTIL_EXPIRY = DSL.field("days_until_expiry", Int::class.java)

    // content_clips
    val SOURCE_VIDEO_ID = DSL.field("source_video_id", Long::class.java)
    val START_TIME_MS = DSL.field("start_time_ms", Long::class.java)
    val END_TIME_MS = DSL.field("end_time_ms", Long::class.java)
    val OUTPUT_URL = DSL.field("output_url", String::class.java)

    // video_captions
    val CAPTION_DATA = DSL.field("caption_data", String::class.java)

    // ai_thumbnails
    val STYLE = DSL.field("style", String::class.java)
    val TEXT_OVERLAY = DSL.field("text_overlay", String::class.java)

    // live_dashboard_alerts / live_alert_configs
    val ALERT_TYPE = DSL.field("type", String::class.java)
    val ALERT_MESSAGE = DSL.field("message", String::class.java)
    val ALERT_SEVERITY = DSL.field("severity", String::class.java)

    // webhook_events
    val EVENT_ID = DSL.field("event_id", String::class.java)
    val EVENT_TYPE = DSL.field("event_type", String::class.java)
    val PAYLOAD = DSL.field("payload", Any::class.java)
    val RETRY_COUNT = DSL.field("retry_count", Int::class.java)
    val MAX_RETRIES = DSL.field("max_retries", Int::class.java)
    val NEXT_RETRY_AT = DSL.field("next_retry_at", java.time.LocalDateTime::class.java)
    val PROCESSED_AT = DSL.field("processed_at", java.time.LocalDateTime::class.java)

    // coupons
    val CODE = DSL.field("code", String::class.java)
    val DISCOUNT_TYPE = DSL.field("discount_type", String::class.java)
    val DISCOUNT_VALUE = DSL.field("discount_value", Int::class.java)
    val APPLICABLE_PLANS = DSL.field("applicable_plans", String::class.java)
    val MIN_BILLING_CYCLE = DSL.field("min_billing_cycle", String::class.java)
    val MAX_USES = DSL.field("max_uses", Int::class.java)
    val USED_COUNT = DSL.field("used_count", Int::class.java)
    val MAX_USES_PER_USER = DSL.field("max_uses_per_user", Int::class.java)
    val VALID_FROM = DSL.field("valid_from", java.time.LocalDateTime::class.java)
    val VALID_UNTIL = DSL.field("valid_until", java.time.LocalDateTime::class.java)
    val ACTIVE = DSL.field("active", Boolean::class.java)

    // coupon_usages
    val COUPON_ID = DSL.field("coupon_id", Long::class.java)
    val APPLIED_AT = DSL.field("applied_at", java.time.LocalDateTime::class.java)
    val DISCOUNT_AMOUNT = DSL.field("discount_amount", Int::class.java)
    val SUBSCRIPTION_ID = DSL.field("subscription_id", Long::class.java)

    // usage_alert_configs
    val THRESHOLD_PERCENT = DSL.field("threshold_percent", Int::class.java)
    val LAST_ALERTED_AT = DSL.field("last_alerted_at", java.time.LocalDateTime::class.java)

    // subscriptions new fields
    val TRIAL_START = DSL.field("trial_start", java.time.LocalDateTime::class.java)
    val TRIAL_END = DSL.field("trial_end", java.time.LocalDateTime::class.java)
    val TRIAL_PLAN_TYPE = DSL.field("trial_plan_type", String::class.java)
    val PAUSED_AT = DSL.field("paused_at", java.time.LocalDateTime::class.java)
    val RESUME_AT = DSL.field("resume_at", java.time.LocalDateTime::class.java)
}
