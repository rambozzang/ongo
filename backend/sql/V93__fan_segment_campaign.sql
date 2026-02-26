-- 팬 세그먼트 캠페인
CREATE TABLE campaign_segments (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    criteria        TEXT NOT NULL,
    fan_count       INT NOT NULL DEFAULT 0,
    avg_engagement  NUMERIC(5,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fan_campaigns (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    segment_id      BIGINT NOT NULL REFERENCES campaign_segments(id),
    segment_name    VARCHAR(200) NOT NULL,
    campaign_type   VARCHAR(30) NOT NULL,
    message         TEXT NOT NULL,
    target_count    INT NOT NULL DEFAULT 0,
    sent_count      INT NOT NULL DEFAULT 0,
    open_rate       NUMERIC(5,2),
    click_rate      NUMERIC(5,2),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    scheduled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaign_segments_user ON campaign_segments(user_id);
CREATE INDEX idx_fan_campaigns_user ON fan_campaigns(user_id);
CREATE INDEX idx_fan_campaigns_status ON fan_campaigns(status);
CREATE INDEX idx_fan_campaigns_segment ON fan_campaigns(segment_id);
