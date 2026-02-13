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
    val AI_CREDITS = DSL.table("ai_credits")
    val AI_CREDIT_TRANSACTIONS = DSL.table("ai_credit_transactions")
    val AI_PURCHASED_CREDITS = DSL.table("ai_purchased_credits")
    val SUBSCRIPTIONS = DSL.table("subscriptions")
    val PAYMENTS = DSL.table("payments")
    val NOTIFICATIONS = DSL.table("notifications")
    val USER_SETTINGS = DSL.table("user_settings")
    val REFRESH_TOKENS = DSL.table("refresh_tokens")
    val COMPETITORS = DSL.table("competitors")
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

    // Collab
    val TEAM_MEMBERS = DSL.table("team_members")
    val INBOX_MESSAGES = DSL.table("inbox_messages")
    val ACTIVITY_LOGS = DSL.table("activity_logs")
    val RECYCLING_SUGGESTIONS = DSL.table("recycling_suggestions")

    // Video Variants
    val VIDEO_VARIANTS = DSL.table("video_variants")

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
    val CANCELLED_AT = DSL.field("cancelled_at", java.time.LocalDateTime::class.java)

    // payments
    val CURRENCY = DSL.field("currency", String::class.java)
    val PG_PROVIDER = DSL.field("pg_provider", String::class.java)
    val PG_TRANSACTION_ID = DSL.field("pg_transaction_id", String::class.java)
    val PAYMENT_METHOD = DSL.field("payment_method", String::class.java)
    val RECEIPT_URL = DSL.field("receipt_url", String::class.java)

    // notifications
    val MESSAGE = DSL.field("message", String::class.java)
    val IS_READ = DSL.field("is_read", Boolean::class.java)
    val REFERENCE_TYPE = DSL.field("reference_type", String::class.java)

    // user_settings
    val DEFAULT_VISIBILITY = DSL.field("default_visibility", String::class.java)
    val DEFAULT_PLATFORMS = DSL.field("default_platforms", Any::class.java)
    val DEFAULT_AI_TONE = DSL.field("default_ai_tone", String::class.java)
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
    val CONTENT = DSL.field("content", String::class.java)
    val SENTIMENT = DSL.field("sentiment", String::class.java)
    val IS_REPLIED = DSL.field("is_replied", Boolean::class.java)
    val REPLY_CONTENT = DSL.field("reply_content", String::class.java)
    val REPLIED_AT = DSL.field("replied_at", java.time.LocalDateTime::class.java)

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

    // video_variants
    val BITRATE_KBPS = DSL.field("bitrate_kbps", Int::class.java)

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
}
