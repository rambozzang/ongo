CREATE TABLE IF NOT EXISTS content_rights (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id VARCHAR(100),
    video_title VARCHAR(500),
    asset_name VARCHAR(300) NOT NULL,
    asset_type VARCHAR(30) NOT NULL DEFAULT 'MUSIC',
    license_type VARCHAR(30) NOT NULL DEFAULT 'FREE',
    license_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(300),
    license_url VARCHAR(500),
    expires_at TIMESTAMPTZ,
    purchased_at TIMESTAMPTZ,
    cost BIGINT DEFAULT 0,
    currency VARCHAR(10) DEFAULT 'KRW',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_content_rights_user_id ON content_rights(user_id);
CREATE INDEX IF NOT EXISTS idx_content_rights_license_status ON content_rights(license_status);
CREATE INDEX IF NOT EXISTS idx_content_rights_expires_at ON content_rights(expires_at);
CREATE INDEX IF NOT EXISTS idx_content_rights_asset_type ON content_rights(asset_type);

CREATE TABLE IF NOT EXISTS rights_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    content_right_id BIGINT NOT NULL REFERENCES content_rights(id) ON DELETE CASCADE,
    asset_name VARCHAR(300) NOT NULL,
    asset_type VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    days_until_expiry INT DEFAULT 0,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rights_alerts_user_id ON rights_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_rights_alerts_is_read ON rights_alerts(is_read);
