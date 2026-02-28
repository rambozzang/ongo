-- V34: 요금제 시스템 v2 테이블 및 확장
-- 트라이얼, 일시정지, 쿠폰, 웹훅 이벤트, 사용량 알림

-- 1. Enum 확장
ALTER TYPE subscription_status ADD VALUE IF NOT EXISTS 'TRIALING';
ALTER TYPE subscription_status ADD VALUE IF NOT EXISTS 'PAUSED';

-- 2. subscriptions 테이블 컬럼 추가
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_start TIMESTAMP;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_end TIMESTAMP;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_plan_type VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS resume_at TIMESTAMP;

-- 3. coupons 테이블
CREATE TABLE IF NOT EXISTS coupons (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(500),
    discount_type   VARCHAR(20) NOT NULL,
    discount_value  INTEGER NOT NULL,
    applicable_plans VARCHAR(200),
    min_billing_cycle VARCHAR(20),
    max_uses        INTEGER,
    used_count      INTEGER NOT NULL DEFAULT 0,
    max_uses_per_user INTEGER NOT NULL DEFAULT 1,
    valid_from      TIMESTAMP NOT NULL,
    valid_until     TIMESTAMP,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4. coupon_usages 테이블
CREATE TABLE IF NOT EXISTS coupon_usages (
    id              BIGSERIAL PRIMARY KEY,
    coupon_id       BIGINT NOT NULL REFERENCES coupons(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    applied_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    discount_amount INTEGER NOT NULL,
    subscription_id BIGINT REFERENCES subscriptions(id),
    UNIQUE(coupon_id, user_id)
);

-- 5. webhook_events 테이블
CREATE TABLE IF NOT EXISTS webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(200) NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP,
    error_message   TEXT,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_webhook_events_status ON webhook_events(status) WHERE status != 'PROCESSED';
CREATE INDEX IF NOT EXISTS idx_webhook_events_next_retry ON webhook_events(next_retry_at) WHERE status = 'FAILED';

-- 6. usage_alert_configs 테이블
CREATE TABLE IF NOT EXISTS usage_alert_configs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    alert_type      VARCHAR(50) NOT NULL,
    threshold_percent INTEGER NOT NULL DEFAULT 80,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    last_alerted_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, alert_type)
);
