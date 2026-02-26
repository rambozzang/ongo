-- 수익 분석기
CREATE TABLE revenue_streams (
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

CREATE TABLE revenue_projections (
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

CREATE INDEX idx_revenue_streams_user ON revenue_streams(user_id);
CREATE INDEX idx_revenue_streams_source ON revenue_streams(source);
CREATE INDEX idx_revenue_streams_period ON revenue_streams(period);
CREATE INDEX idx_revenue_projections_user ON revenue_projections(user_id);
CREATE INDEX idx_revenue_projections_channel ON revenue_projections(channel_id);
