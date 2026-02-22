-- V19__brand_deals.sql
-- 브랜드 딜 관리

CREATE TABLE brand_deals (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    brand_name      VARCHAR(200) NOT NULL,
    contact_name    VARCHAR(100),
    contact_email   VARCHAR(200),
    deal_value      BIGINT,  -- KRW
    currency        VARCHAR(10) DEFAULT 'KRW',
    status          VARCHAR(30) NOT NULL DEFAULT 'INQUIRY',  -- INQUIRY, NEGOTIATION, CONTRACTED, IN_PROGRESS, COMPLETED, CANCELLED
    deadline        DATE,
    deliverables    JSONB DEFAULT '[]',
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_brand_deals_user ON brand_deals(user_id);
CREATE INDEX idx_brand_deals_status ON brand_deals(status);

CREATE TABLE media_kits (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    display_name    VARCHAR(200) NOT NULL,
    bio             TEXT,
    categories      JSONB DEFAULT '[]',
    social_links    JSONB DEFAULT '{}',
    stats_snapshot  JSONB DEFAULT '{}',
    rate_card       JSONB DEFAULT '{}',
    is_public       BOOLEAN NOT NULL DEFAULT FALSE,
    slug            VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_kits_user ON media_kits(user_id);
CREATE UNIQUE INDEX idx_media_kits_slug ON media_kits(slug) WHERE slug IS NOT NULL;
