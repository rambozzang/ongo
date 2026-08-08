CREATE TABLE webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_key VARCHAR(200) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    lease_owner VARCHAR(200),
    lease_until TIMESTAMP,
    status_code INTEGER,
    response_body TEXT,
    sent_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook_delivery_event UNIQUE (webhook_id, event_key)
);

CREATE INDEX idx_webhook_deliveries_due
    ON webhook_deliveries(status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_webhook_deliveries_webhook_created
    ON webhook_deliveries(webhook_id, created_at DESC);
