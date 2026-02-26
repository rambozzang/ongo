CREATE TABLE IF NOT EXISTS brands (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    logo_url VARCHAR(500),
    color VARCHAR(20) NOT NULL DEFAULT 'BLUE',
    category VARCHAR(100),
    assigned_editors VARCHAR(500) DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brands_user_id ON brands(user_id);
CREATE INDEX IF NOT EXISTS idx_brands_is_active ON brands(is_active);

CREATE TABLE IF NOT EXISTS brand_schedule_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    assigned_to VARCHAR(200),
    video_id VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_schedule_items_user_id ON brand_schedule_items(user_id);
CREATE INDEX IF NOT EXISTS idx_brand_schedule_items_brand_id ON brand_schedule_items(brand_id);
CREATE INDEX IF NOT EXISTS idx_brand_schedule_items_scheduled_at ON brand_schedule_items(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_brand_schedule_items_status ON brand_schedule_items(status);
