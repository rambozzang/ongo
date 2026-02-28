-- V30: 수익/커머스 테이블
-- commerce, funding, split, revenue_analyzer, sponsorship

-- 1. 소셜 커머스
CREATE TABLE IF NOT EXISTS commerce_platforms (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform_type VARCHAR(30) NOT NULL,
    platform_name VARCHAR(100),
    access_token_enc TEXT,
    status VARCHAR(20) DEFAULT 'CONNECTED',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS commerce_products (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    commerce_platform_id BIGINT REFERENCES commerce_platforms(id),
    product_name VARCHAR(200),
    product_url TEXT,
    image_url TEXT,
    price DECIMAL(12,2),
    affiliate_link TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS product_video_links (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES commerce_products(id),
    video_id BIGINT NOT NULL REFERENCES videos(id),
    clicks INTEGER DEFAULT 0,
    conversions INTEGER DEFAULT 0,
    revenue DECIMAL(12,2) DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(product_id, video_id)
);

-- 2. 팬 펀딩
CREATE TABLE IF NOT EXISTS funding_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    source VARCHAR(50) NOT NULL DEFAULT 'SUPER_CHAT',
    platform VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    donor_name VARCHAR(200),
    message TEXT,
    video_id VARCHAR(100),
    video_title VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_funding_transactions_user_id ON funding_transactions(user_id);

CREATE TABLE IF NOT EXISTS funding_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    target_amount BIGINT NOT NULL DEFAULT 0,
    current_amount BIGINT NOT NULL DEFAULT 0,
    deadline TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_funding_goals_user_id ON funding_goals(user_id);

-- 3. 수익 분배
CREATE TABLE IF NOT EXISTS revenue_splits (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    total_amount    BIGINT NOT NULL DEFAULT 0,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    period          VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_revenue_splits_workspace ON revenue_splits(workspace_id);

CREATE TABLE IF NOT EXISTS split_members (
    id              BIGSERIAL PRIMARY KEY,
    split_id        BIGINT NOT NULL REFERENCES revenue_splits(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(200),
    role            VARCHAR(50),
    percentage      DECIMAL(5,2) NOT NULL,
    amount          BIGINT NOT NULL DEFAULT 0,
    payment_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_split_members_split ON split_members(split_id);

-- 4. 수익 분석기
CREATE TABLE IF NOT EXISTS revenue_streams (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    channel_id      BIGINT NOT NULL,
    channel_name    VARCHAR(200) NOT NULL,
    source          VARCHAR(30) NOT NULL,
    amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    period          VARCHAR(10) NOT NULL,
    growth          NUMERIC(6,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_revenue_streams_user ON revenue_streams(user_id);

CREATE TABLE IF NOT EXISTS revenue_projections (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    channel_id          BIGINT NOT NULL,
    source              VARCHAR(30) NOT NULL,
    current_monthly     NUMERIC(15,2) NOT NULL DEFAULT 0,
    projected_monthly   NUMERIC(15,2) NOT NULL DEFAULT 0,
    confidence          INT NOT NULL DEFAULT 0,
    projection_date     VARCHAR(10) NOT NULL,
    factors             JSONB NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_revenue_projections_user ON revenue_projections(user_id);

-- 5. 스폰서십 트래커
CREATE TABLE IF NOT EXISTS sponsorships (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    brand_name      VARCHAR(200)    NOT NULL,
    brand_logo      VARCHAR(500),
    contact_name    VARCHAR(100)    NOT NULL,
    contact_email   VARCHAR(200)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'INQUIRY',
    deal_value      BIGINT          NOT NULL DEFAULT 0,
    currency        VARCHAR(10)     NOT NULL DEFAULT 'KRW',
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    notes           TEXT,
    contract_url    VARCHAR(500),
    payment_status  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    paid_amount     BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sponsorships_user_id ON sponsorships(user_id);

CREATE TABLE IF NOT EXISTS sponsorship_deliverables (
    id              BIGSERIAL       PRIMARY KEY,
    sponsorship_id  BIGINT          NOT NULL REFERENCES sponsorships(id) ON DELETE CASCADE,
    type            VARCHAR(30)     NOT NULL,
    title           VARCHAR(300)    NOT NULL,
    description     TEXT,
    due_date        DATE            NOT NULL,
    is_completed    BOOLEAN         NOT NULL DEFAULT false,
    video_id        VARCHAR(100),
    platform        VARCHAR(30)     NOT NULL,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_deliverables_sponsorship ON sponsorship_deliverables(sponsorship_id);
