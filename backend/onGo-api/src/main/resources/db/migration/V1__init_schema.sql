-- ============================================================================
-- onGo - Creator Multi-Platform Management SaaS
-- Flyway Migration: V1__init_schema.sql
-- PostgreSQL 16
-- ============================================================================

-- ============================================================================
-- 1. ENUM TYPES
-- ============================================================================

CREATE TYPE auth_provider AS ENUM ('GOOGLE', 'KAKAO');
CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');
CREATE TYPE plan_type AS ENUM ('FREE', 'STARTER', 'PRO', 'BUSINESS');
CREATE TYPE platform_type AS ENUM ('YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP');
CREATE TYPE channel_status AS ENUM ('ACTIVE', 'EXPIRED', 'DISCONNECTED');
CREATE TYPE video_status AS ENUM ('DRAFT', 'UPLOADING', 'PUBLISHED', 'FAILED');
CREATE TYPE upload_status AS ENUM ('UPLOADING', 'PROCESSING', 'REVIEW', 'PUBLISHED', 'FAILED', 'REJECTED');
CREATE TYPE visibility_type AS ENUM ('PUBLIC', 'PRIVATE', 'UNLISTED');
CREATE TYPE schedule_status AS ENUM ('SCHEDULED', 'PUBLISHED', 'FAILED', 'CANCELLED');
CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'CANCELLED', 'PAST_DUE', 'FREE');
CREATE TYPE billing_cycle AS ENUM ('MONTHLY', 'YEARLY');
CREATE TYPE payment_type AS ENUM ('SUBSCRIPTION', 'CREDIT');
CREATE TYPE payment_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
CREATE TYPE credit_tx_type AS ENUM ('DEDUCT', 'CHARGE', 'FREE_RESET');
CREATE TYPE purchased_credit_status AS ENUM ('ACTIVE', 'EXPIRED', 'EXHAUSTED');
CREATE TYPE notification_type AS ENUM ('UPLOAD_COMPLETE', 'UPLOAD_FAILED', 'CREDIT_LOW', 'SCHEDULE_REMINDER', 'COMMENT', 'SYSTEM');

-- ============================================================================
-- 2. TABLES
-- ============================================================================

-- --------------------------------------------------------------------------
-- users: 사용자 정보
-- --------------------------------------------------------------------------
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    name                VARCHAR(100) NOT NULL,
    nickname            VARCHAR(50),
    profile_image_url   TEXT,
    provider            auth_provider NOT NULL,
    provider_id         VARCHAR(255) NOT NULL,
    plan_type           plan_type NOT NULL DEFAULT 'FREE',
    category            VARCHAR(50),
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    role                user_role NOT NULL DEFAULT 'USER',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_provider UNIQUE (provider, provider_id)
);

COMMENT ON TABLE users IS '사용자 계정 (소셜 로그인 전용: Google/Kakao)';
COMMENT ON COLUMN users.category IS '크리에이터 카테고리 (뷰티/먹방/게임/일상/교육/IT/여행/음악 등)';
COMMENT ON COLUMN users.plan_type IS '구독 플랜: FREE, STARTER(9,900원), PRO(19,900원), BUSINESS(49,900원)';

-- --------------------------------------------------------------------------
-- channels: 연동 플랫폼 채널
-- --------------------------------------------------------------------------
CREATE TABLE channels (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform            platform_type NOT NULL,
    platform_channel_id VARCHAR(255) NOT NULL,
    channel_name        VARCHAR(255) NOT NULL,
    channel_url         TEXT,
    subscriber_count    INTEGER DEFAULT 0,
    profile_image_url   TEXT,
    access_token        TEXT,
    refresh_token       TEXT,
    token_expires_at    TIMESTAMP,
    status              channel_status NOT NULL DEFAULT 'ACTIVE',
    connected_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_channels_user_platform UNIQUE (user_id, platform)
);

COMMENT ON TABLE channels IS '플랫폼 연동 채널 (YouTube/TikTok/Instagram/Naver Clip)';
COMMENT ON COLUMN channels.access_token IS 'AES-256 암호화된 플랫폼 액세스 토큰';
COMMENT ON COLUMN channels.refresh_token IS 'AES-256 암호화된 플랫폼 리프레시 토큰';

-- --------------------------------------------------------------------------
-- videos: 업로드 영상 원본 정보
-- --------------------------------------------------------------------------
CREATE TABLE videos (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    tags                TEXT[],
    category            VARCHAR(50),
    file_url            TEXT,
    file_size_bytes     BIGINT,
    duration_seconds    INTEGER,
    resolution          VARCHAR(20),
    original_filename   VARCHAR(500),
    content_hash        VARCHAR(64),
    thumbnail_urls      JSONB,
    status              video_status NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_videos_file_size CHECK (file_size_bytes IS NULL OR file_size_bytes > 0),
    CONSTRAINT chk_videos_duration CHECK (duration_seconds IS NULL OR duration_seconds > 0)
);

COMMENT ON TABLE videos IS '업로드 영상 원본 메타데이터';
COMMENT ON COLUMN videos.content_hash IS 'SHA-256 해시 - 중복 업로드 방지용';
COMMENT ON COLUMN videos.thumbnail_urls IS 'JSONB 배열: 자동 추출 썸네일 3장 URL';
COMMENT ON COLUMN videos.status IS 'DRAFT → UPLOADING → PUBLISHED/FAILED';

-- --------------------------------------------------------------------------
-- video_uploads: 플랫폼별 업로드 상태
-- --------------------------------------------------------------------------
CREATE TABLE video_uploads (
    id                  BIGSERIAL PRIMARY KEY,
    video_id            BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    platform            platform_type NOT NULL,
    platform_video_id   VARCHAR(255),
    status              upload_status NOT NULL DEFAULT 'UPLOADING',
    error_message       TEXT,
    platform_url        TEXT,
    published_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_video_uploads_video_platform UNIQUE (video_id, platform)
);

COMMENT ON TABLE video_uploads IS '플랫폼별 영상 업로드 상태 추적';
COMMENT ON COLUMN video_uploads.status IS 'UPLOADING → PROCESSING → REVIEW → PUBLISHED/FAILED/REJECTED';

-- --------------------------------------------------------------------------
-- video_platform_meta: 플랫폼별 메타데이터 오버라이드
-- --------------------------------------------------------------------------
CREATE TABLE video_platform_meta (
    id                  BIGSERIAL PRIMARY KEY,
    video_upload_id     BIGINT NOT NULL REFERENCES video_uploads(id) ON DELETE CASCADE,
    title               TEXT,
    description         TEXT,
    tags                TEXT[],
    visibility          visibility_type NOT NULL DEFAULT 'PUBLIC',
    custom_thumbnail_url TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE video_platform_meta IS '플랫폼별 메타데이터 커스터마이징 (AI 생성 결과 저장)';

-- --------------------------------------------------------------------------
-- schedules: 예약 업로드
-- --------------------------------------------------------------------------
CREATE TABLE schedules (
    id                  BIGSERIAL PRIMARY KEY,
    video_id            BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scheduled_at        TIMESTAMP NOT NULL,
    status              schedule_status NOT NULL DEFAULT 'SCHEDULED',
    platforms           JSONB NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_schedules_future CHECK (scheduled_at > created_at)
);

COMMENT ON TABLE schedules IS '영상 예약 업로드 관리';
COMMENT ON COLUMN schedules.platforms IS 'JSONB: 플랫폼별 개별 시간 설정 배열 [{platform, scheduled_at, config}]';

-- --------------------------------------------------------------------------
-- analytics_daily: 일별 영상 성과 (파티셔닝 테이블)
-- --------------------------------------------------------------------------
CREATE TABLE analytics_daily (
    id                  BIGSERIAL,
    video_upload_id     BIGINT NOT NULL,
    date                DATE NOT NULL,
    views               INTEGER NOT NULL DEFAULT 0,
    likes               INTEGER NOT NULL DEFAULT 0,
    comments_count      INTEGER NOT NULL DEFAULT 0,
    shares              INTEGER NOT NULL DEFAULT 0,
    watch_time_seconds  BIGINT NOT NULL DEFAULT 0,
    subscriber_gained   INTEGER NOT NULL DEFAULT 0,
    revenue_micro       BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, date),
    CONSTRAINT uq_analytics_upload_date UNIQUE (video_upload_id, date),
    CONSTRAINT chk_analytics_views CHECK (views >= 0),
    CONSTRAINT chk_analytics_likes CHECK (likes >= 0),
    CONSTRAINT chk_analytics_comments CHECK (comments_count >= 0),
    CONSTRAINT chk_analytics_shares CHECK (shares >= 0),
    CONSTRAINT chk_analytics_watch_time CHECK (watch_time_seconds >= 0)
) PARTITION BY RANGE (date);

COMMENT ON TABLE analytics_daily IS '일별 영상 성과 데이터 (월별 파티셔닝)';
COMMENT ON COLUMN analytics_daily.revenue_micro IS '수익 (마이크로 단위: 1원 = 1,000,000 micro)';

-- Monthly partitions for 2026
CREATE TABLE analytics_daily_2026_01 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE analytics_daily_2026_02 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE analytics_daily_2026_03 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE analytics_daily_2026_04 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE analytics_daily_2026_05 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE analytics_daily_2026_06 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE analytics_daily_2026_07 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE analytics_daily_2026_08 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE analytics_daily_2026_09 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE analytics_daily_2026_10 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE analytics_daily_2026_11 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE analytics_daily_2026_12 PARTITION OF analytics_daily
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- --------------------------------------------------------------------------
-- Auto-create monthly partitions for analytics_daily
-- Creates partitions 3 months ahead to prevent insert failures.
-- Should be called by a scheduled task (e.g., monthly cron or Spring @Scheduled).
-- --------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_create_analytics_partitions(months_ahead INTEGER DEFAULT 3)
RETURNS void AS $$
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
$$ LANGUAGE plpgsql;

-- --------------------------------------------------------------------------
-- ai_credits: 사용자별 AI 크레딧 잔액
-- --------------------------------------------------------------------------
CREATE TABLE ai_credits (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    balance             INTEGER NOT NULL DEFAULT 0,
    free_monthly        INTEGER NOT NULL DEFAULT 30,
    free_remaining      INTEGER NOT NULL DEFAULT 30,
    free_reset_date     DATE NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ai_credits_balance CHECK (balance >= 0),
    CONSTRAINT chk_ai_credits_free CHECK (free_remaining >= 0),
    CONSTRAINT chk_ai_credits_free_monthly CHECK (free_monthly >= 0)
);

COMMENT ON TABLE ai_credits IS '사용자별 AI 크레딧 잔액 (비관적 잠금 FOR UPDATE 사용)';
COMMENT ON COLUMN ai_credits.free_monthly IS '플랜별 무료 월간 크레딧 (FREE:30, STARTER:100, PRO:300, BUSINESS:1000)';
COMMENT ON COLUMN ai_credits.free_reset_date IS '무료 크레딧 리셋 날짜 (매월 1일)';

-- --------------------------------------------------------------------------
-- ai_credit_transactions: 크레딧 사용/충전 이력
-- --------------------------------------------------------------------------
CREATE TABLE ai_credit_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                credit_tx_type NOT NULL,
    amount              INTEGER NOT NULL,
    balance_after       INTEGER NOT NULL,
    feature             VARCHAR(50),
    reference_id        BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ai_credit_transactions IS '크레딧 트랜잭션 내역: DEDUCT(차감), CHARGE(충전), FREE_RESET(월초 리셋)';
COMMENT ON COLUMN ai_credit_transactions.amount IS '양수: 충전, 음수: 차감';
COMMENT ON COLUMN ai_credit_transactions.feature IS 'AI 기능 식별자: meta_gen, hashtag, stt, script_analysis, reply, schedule_suggest, ideas, report';

-- --------------------------------------------------------------------------
-- ai_purchased_credits: 구매 크레딧 (유효기간별 FIFO 관리)
-- --------------------------------------------------------------------------
CREATE TABLE ai_purchased_credits (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_name        VARCHAR(50) NOT NULL,
    total_credits       INTEGER NOT NULL,
    remaining           INTEGER NOT NULL,
    price               INTEGER NOT NULL,
    purchased_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP NOT NULL,
    status              purchased_credit_status NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_purchased_credits_total CHECK (total_credits > 0),
    CONSTRAINT chk_purchased_credits_remaining CHECK (remaining >= 0),
    CONSTRAINT chk_purchased_credits_price CHECK (price >= 0)
);

COMMENT ON TABLE ai_purchased_credits IS '구매 크레딧 패키지 (만료 임박순 FIFO 차감)';

-- --------------------------------------------------------------------------
-- subscriptions: 구독 정보
-- --------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    plan_type               plan_type NOT NULL DEFAULT 'FREE',
    status                  subscription_status NOT NULL DEFAULT 'FREE',
    price                   INTEGER NOT NULL DEFAULT 0,
    billing_cycle           billing_cycle,
    current_period_start    TIMESTAMP,
    current_period_end      TIMESTAMP,
    next_billing_date       TIMESTAMP,
    cancelled_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_subscriptions_price CHECK (price >= 0)
);

COMMENT ON TABLE subscriptions IS '사용자 구독 정보 (1인 1구독)';

-- --------------------------------------------------------------------------
-- payments: 결제 이력
-- --------------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                payment_type NOT NULL,
    amount              INTEGER NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'KRW',
    status              payment_status NOT NULL DEFAULT 'PENDING',
    pg_provider         VARCHAR(50),
    pg_transaction_id   VARCHAR(100),
    payment_method      VARCHAR(50),
    receipt_url         TEXT,
    description         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payments_amount CHECK (amount > 0)
);

COMMENT ON TABLE payments IS '결제 이력 (구독 결제 + 크레딧 충전)';

-- --------------------------------------------------------------------------
-- notifications: 알림
-- --------------------------------------------------------------------------
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type                notification_type NOT NULL,
    title               VARCHAR(200) NOT NULL,
    message             TEXT,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    reference_type      VARCHAR(50),
    reference_id        BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE notifications IS '사용자 알림 (업로드 완료/실패, 크레딧 부족, 예약 리마인더, 댓글, 시스템)';

-- --------------------------------------------------------------------------
-- user_settings: 사용자 설정
-- --------------------------------------------------------------------------
CREATE TABLE user_settings (
    id                              BIGSERIAL PRIMARY KEY,
    user_id                         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    default_visibility              visibility_type NOT NULL DEFAULT 'PUBLIC',
    default_platforms               JSONB,
    default_ai_tone                 VARCHAR(20) NOT NULL DEFAULT 'friendly',
    notification_upload             BOOLEAN NOT NULL DEFAULT TRUE,
    notification_comment            VARCHAR(20) NOT NULL DEFAULT 'realtime',
    notification_credit_threshold   INTEGER NOT NULL DEFAULT 20,
    notification_schedule_reminder  INTEGER NOT NULL DEFAULT 60,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_settings_credit_threshold CHECK (notification_credit_threshold BETWEEN 0 AND 100),
    CONSTRAINT chk_settings_schedule_reminder CHECK (notification_schedule_reminder > 0)
);

COMMENT ON TABLE user_settings IS '사용자 기본 설정';
COMMENT ON COLUMN user_settings.notification_comment IS '댓글 알림 빈도: realtime, daily, off';
COMMENT ON COLUMN user_settings.notification_credit_threshold IS '크레딧 잔여 알림 임계값 (%)';
COMMENT ON COLUMN user_settings.notification_schedule_reminder IS '예약 리마인더 사전 알림 시간 (분)';

-- --------------------------------------------------------------------------
-- refresh_tokens: JWT 리프레시 토큰
-- --------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token               VARCHAR(512) NOT NULL UNIQUE,
    expires_at          TIMESTAMP NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE refresh_tokens IS 'JWT 리프레시 토큰 (Access 30분, Refresh 7일)';

-- ============================================================================
-- 3. INDEXES
-- ============================================================================
-- Note: PostgreSQL automatically creates indexes for PRIMARY KEY and UNIQUE constraints.
-- Indexes below are only for columns/patterns NOT already covered by constraints.

-- channels
-- idx_channels_user_id omitted: covered by uq_channels_user_platform (user_id, platform)
CREATE INDEX idx_channels_status ON channels(user_id, status);

-- videos
CREATE INDEX idx_videos_user_created ON videos(user_id, created_at DESC);
CREATE INDEX idx_videos_status ON videos(status);
CREATE INDEX idx_videos_content_hash ON videos(content_hash) WHERE content_hash IS NOT NULL;

-- video_uploads
-- idx_video_uploads_video_platform omitted: covered by uq_video_uploads_video_platform
CREATE INDEX idx_video_uploads_active ON video_uploads(status)
    WHERE status IN ('UPLOADING', 'PROCESSING');
CREATE INDEX idx_video_uploads_platform_video ON video_uploads(platform_video_id)
    WHERE platform_video_id IS NOT NULL;

-- video_platform_meta
CREATE INDEX idx_video_platform_meta_upload ON video_platform_meta(video_upload_id);

-- schedules
CREATE INDEX idx_schedules_user ON schedules(user_id);
CREATE INDEX idx_schedules_pending ON schedules(scheduled_at, status)
    WHERE status = 'SCHEDULED';
CREATE INDEX idx_schedules_video ON schedules(video_id);

-- analytics_daily
-- idx_analytics_upload_date omitted: covered by uq_analytics_upload_date

-- ai_credit_transactions
CREATE INDEX idx_credit_tx_user_date ON ai_credit_transactions(user_id, created_at DESC);

-- ai_purchased_credits
CREATE INDEX idx_purchased_user_active ON ai_purchased_credits(user_id, status, expires_at)
    WHERE status = 'ACTIVE';

-- subscriptions
-- idx_subscriptions_user omitted: covered by UNIQUE on user_id
CREATE INDEX idx_subscriptions_next_billing ON subscriptions(next_billing_date)
    WHERE status = 'ACTIVE';

-- payments
CREATE INDEX idx_payments_user_date ON payments(user_id, created_at DESC);
CREATE INDEX idx_payments_pg_tx ON payments(pg_transaction_id)
    WHERE pg_transaction_id IS NOT NULL;

-- notifications
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_user_type ON notifications(user_id, type);

-- refresh_tokens
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- ============================================================================
-- 4. FUNCTIONS: updated_at 자동 갱신 트리거
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all tables with updated_at
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_channels_updated_at
    BEFORE UPDATE ON channels FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_videos_updated_at
    BEFORE UPDATE ON videos FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_video_uploads_updated_at
    BEFORE UPDATE ON video_uploads FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_video_platform_meta_updated_at
    BEFORE UPDATE ON video_platform_meta FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_schedules_updated_at
    BEFORE UPDATE ON schedules FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_ai_credits_updated_at
    BEFORE UPDATE ON ai_credits FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_subscriptions_updated_at
    BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

CREATE TRIGGER trg_user_settings_updated_at
    BEFORE UPDATE ON user_settings FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- ============================================================================
-- 5. SEED DATA: 플랜 구성 참조 (주석)
-- ============================================================================

-- Plan Configuration Reference:
-- ┌──────────┬──────────┬──────────────┬──────────────┬──────────────────┐
-- │ Feature  │  FREE    │ STARTER      │ PRO          │ BUSINESS         │
-- │          │          │ (9,900 KRW)  │ (19,900 KRW) │ (49,900 KRW)     │
-- ├──────────┼──────────┼──────────────┼──────────────┼──────────────────┤
-- │ Platforms│ 1        │ 3            │ 4            │ 4 + API          │
-- │ Uploads  │ 5/month  │ 30/month     │ 100/month    │ Unlimited        │
-- │ Schedule │ None     │ 7 days       │ 30 days      │ 90 days          │
-- │ Analytics│ 7 days   │ 30 days      │ 1 year       │ All              │
-- │ Storage  │ 1 GB     │ 10 GB        │ 50 GB        │ 200 GB           │
-- │ Comments │ No       │ No           │ Yes          │ Yes              │
-- │ Team     │ None     │ None         │ 2 members    │ 10 members       │
-- │ AI Creds │ 30/month │ 100/month    │ 300/month    │ 1,000/month      │
-- │ Support  │ Community│ Email        │ Priority     │ Dedicated Mgr    │
-- │ Retention│ 30 days  │ 90 days      │ Permanent    │ Permanent        │
-- └──────────┴──────────┴──────────────┴──────────────┴──────────────────┘

-- AI Credit Package Reference:
-- ┌──────────────┬─────────┬───────────┬──────────┬───────────┐
-- │ Package      │ Credits │ Price     │ Per Unit │ Validity  │
-- ├──────────────┼─────────┼───────────┼──────────┼───────────┤
-- │ Starter Pack │ 500     │ 4,900 KRW │ 9.8 KRW  │ 30 days   │
-- │ Basic Pack   │ 1,200   │ 9,900 KRW │ 8.3 KRW  │ 60 days   │
-- │ Pro Pack     │ 3,000   │ 19,900 KRW│ 6.6 KRW  │ 90 days   │
-- │ Business Pack│ 10,000  │ 49,900 KRW│ 5.0 KRW  │ 180 days  │
-- └──────────────┴─────────┴───────────┴──────────┴───────────┘

-- AI Feature Credit Costs:
-- ┌──────────────────────┬─────────┐
-- │ Feature              │ Credits │
-- ├──────────────────────┼─────────┤
-- │ Title/Desc Gen       │ 5       │
-- │ Hashtag Suggest      │ 3       │
-- │ Video STT            │ 10      │
-- │ Script Analysis      │ 5       │
-- │ Comment Reply        │ 2       │
-- │ Schedule Suggest     │ 3       │
-- │ Content Ideas        │ 5       │
-- │ Performance Report   │ 8       │
-- └──────────────────────┴─────────┘
