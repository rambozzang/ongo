-- 스폰서십 트래커
CREATE TABLE sponsorships (
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

CREATE TABLE sponsorship_deliverables (
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

CREATE INDEX idx_sponsorships_user_id ON sponsorships(user_id);
CREATE INDEX idx_sponsorships_status ON sponsorships(status);
CREATE INDEX idx_deliverables_sponsorship ON sponsorship_deliverables(sponsorship_id);
CREATE INDEX idx_deliverables_due_date ON sponsorship_deliverables(due_date);
