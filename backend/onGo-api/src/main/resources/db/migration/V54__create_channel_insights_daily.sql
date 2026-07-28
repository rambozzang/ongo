-- --------------------------------------------------------------------------
-- channel_insights_daily: 채널 단위 일별 인사이트 (트래픽 소스 / 인구통계)
--
-- AnalyticsJooqRepository 는 처음부터 이 테이블을 읽고 썼지만 스키마에 존재하지 않아
-- 조회 시 "relation does not exist" 로 실패했다. 그 결과 아래 엔드포인트가 상시 500 이었다.
--   GET /api/v1/analytics/traffic-sources
--   GET /api/v1/analytics/demographics
--
-- 주의: 이 테이블에 데이터를 넣는 upsertChannelInsights 의 호출부가 아직 없다.
-- 테이블이 생기면 500 대신 "데이터 없음"이 반환되지만, 실제 수집 파이프라인을 붙이기
-- 전까지는 계속 비어 있다.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel_insights_daily (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL,
    platform             VARCHAR(30) NOT NULL,
    date                 DATE NOT NULL,
    -- 유입 경로별 조회수: {"SEARCH": 1200, "SUGGESTED": 800, ...}
    traffic_source       JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- 연령대별 비율(%): {"18-24": 32.5, "25-34": 41.0, ...}
    demographics_age     JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- 성별 비율(%): {"male": 62.0, "female": 38.0}
    demographics_gender  JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- 국가별 조회수: {"KR": 8200, "US": 1500, ...}
    demographics_country JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_channel_insights_user_platform_date UNIQUE (user_id, platform, date)
);

COMMENT ON TABLE channel_insights_daily IS '채널 일별 인사이트 (트래픽 소스·인구통계)';

CREATE INDEX IF NOT EXISTS idx_channel_insights_user_date
    ON channel_insights_daily (user_id, date DESC);
