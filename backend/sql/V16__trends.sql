-- V16__trends.sql
-- 트렌드 모니터링

CREATE TABLE trends (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(100),
    keyword     VARCHAR(200) NOT NULL,
    score       DOUBLE PRECISION NOT NULL DEFAULT 0,
    source      VARCHAR(50) NOT NULL,  -- YOUTUBE, GOOGLE_TRENDS, INTERNAL
    platform    VARCHAR(50),
    region      VARCHAR(10) DEFAULT 'KR',
    date        DATE NOT NULL DEFAULT CURRENT_DATE,
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trends_date ON trends(date);
CREATE INDEX idx_trends_keyword ON trends(keyword);
CREATE INDEX idx_trends_category ON trends(category);

CREATE TABLE trend_alerts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    keyword     VARCHAR(200) NOT NULL,
    threshold   DOUBLE PRECISION NOT NULL DEFAULT 50,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trend_alerts_user ON trend_alerts(user_id);
