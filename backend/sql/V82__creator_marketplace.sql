-- 크리에이터 마켓플레이스
CREATE TABLE marketplace_listings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    creator_name    VARCHAR(200) NOT NULL,
    service_type    VARCHAR(50) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    price           BIGINT NOT NULL DEFAULT 0,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    rating          NUMERIC(3,2) NOT NULL DEFAULT 0,
    review_count    INT NOT NULL DEFAULT 0,
    delivery_days   INT NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE marketplace_orders (
    id              BIGSERIAL PRIMARY KEY,
    listing_id      BIGINT NOT NULL REFERENCES marketplace_listings(id),
    buyer_id        BIGINT NOT NULL REFERENCES users(id),
    seller_id       BIGINT NOT NULL REFERENCES users(id),
    buyer_name      VARCHAR(200) NOT NULL,
    seller_name     VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_price     BIGINT NOT NULL DEFAULT 0,
    order_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivery_date   TIMESTAMPTZ
);

CREATE INDEX idx_mkt_listings_user ON marketplace_listings(user_id);
CREATE INDEX idx_mkt_listings_service ON marketplace_listings(service_type);
CREATE INDEX idx_mkt_listings_active ON marketplace_listings(is_active);
CREATE INDEX idx_mkt_orders_buyer ON marketplace_orders(buyer_id);
CREATE INDEX idx_mkt_orders_seller ON marketplace_orders(seller_id);
CREATE INDEX idx_mkt_orders_status ON marketplace_orders(status);
