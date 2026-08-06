-- 기본 프로필에서 살아 있는 엔드포인트가 참조하는 스키마를 코드에 맞춘다.
--
-- V61(competitor_analytics_daily) 을 만든 뒤 같은 결함이 더 있는지 전수 조사했다.
-- Tables.kt 의 DSL.table 선언 127개를 신선한 PostgreSQL 16(마이그레이션 62개
-- 전량 적용, 실존 테이블 241개)의 information_schema 와 대조한 결과다.
--
-- 누락 테이블 6개 중 2개(ab_test_results, test_variants)는 제외했다.
-- ABTestResultRepository / TestVariantRepository 를 application·api 에서
-- 주입받는 곳이 0건인 죽은 코드라 테이블을 만들 이유가 없다.
--
-- 나머지 4개는 @Profile("wip") 이 없는 컨트롤러·유스케이스가 쓰고 프론트가
-- 실제로 호출한다. competitor 건과 달리 예외를 삼키는 곳이 없어 사용자에게
-- 500 이 그대로 나간다.
--   brand_deals        RevenueController /analytics/revenue/brand-deals,
--                      BrandDealController /brand-deals  (api/revenue.ts, api/branddeal.ts)
--   trends, trend_alerts  TrendController
--   video_translations TranslationController (api/video.ts)
--
-- 컬럼과 타입은 추측이 아니라 각 저장소의 실사용과 도메인 모델 기본값에서 도출했다.

-- ---------------------------------------------------------------------------
-- 1. brand_deals
--    BrandDealJooqRepository(CRUD) + RevenueJooqRepository.getBrandDealRevenue
--    후자가 select 에 Fields.PLATFORM 을 포함하므로 platform 컬럼이 반드시 필요하다.
--    쓰기 경로에는 안 나와서 정적 쓰기 감사만으로는 놓쳤을 컬럼이다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brand_deals (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    brand_name    VARCHAR(200) NOT NULL,
    contact_name  VARCHAR(200),
    contact_email VARCHAR(300),
    deal_value    BIGINT,
    currency      VARCHAR(10) NOT NULL DEFAULT 'KRW',
    status        VARCHAR(30) NOT NULL DEFAULT 'INQUIRY',
    deadline      DATE,
    deliverables  VARCHAR(500) DEFAULT '[]',
    notes         TEXT,
    platform      VARCHAR(20),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- findDealsByUserId: user_id [+ status] ORDER BY created_at DESC
-- getBrandDealRevenue: user_id + created_at 범위 ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_brand_deals_user_created
    ON brand_deals (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 2. trends
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trends (
    id         BIGSERIAL PRIMARY KEY,
    category   VARCHAR(100),
    keyword    VARCHAR(200) NOT NULL,
    score      DOUBLE PRECISION NOT NULL DEFAULT 0,
    source     VARCHAR(50) NOT NULL,
    platform   VARCHAR(20),
    region     VARCHAR(10) NOT NULL DEFAULT 'KR',
    date       DATE NOT NULL DEFAULT CURRENT_DATE,
    metadata   TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- findByDate: date 등호 [+ category][+ source] ORDER BY score DESC
CREATE INDEX IF NOT EXISTS idx_trends_date_score
    ON trends (date, score DESC);

-- searchByKeyword 는 keyword ILIKE '%..%' 라 선행 와일드카드다. btree 로 못 탄다.
-- pg_trgm 확장이 필요한데 확장 도입은 이 마이그레이션의 범위를 넘어서
-- 넣지 않았다. 데이터가 쌓여 느려지면 그때 별건으로 다룬다.

-- ---------------------------------------------------------------------------
-- 3. trend_alerts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trend_alerts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword    VARCHAR(200) NOT NULL,
    threshold  DOUBLE PRECISION NOT NULL DEFAULT 50,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- findAlertsByUserId: user_id ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_trend_alerts_user_created
    ON trend_alerts (user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 4. video_translations
--    findByVideoIdAndLanguage 가 fetchOne() 이라 (video_id, language) 는 유일해야
--    한다. 유일하지 않으면 jOOQ 가 TooManyRowsException 을 던져 500 이 된다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS video_translations (
    id               BIGSERIAL PRIMARY KEY,
    video_id         BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    language         VARCHAR(10) NOT NULL,
    title            VARCHAR(500),
    description      TEXT,
    tags             VARCHAR(500) DEFAULT '[]',
    subtitle_content TEXT,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_video_translations_video_language UNIQUE (video_id, language)
);

-- findByVideoId: video_id ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_video_translations_video_created
    ON video_translations (video_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 5. media_kits — 테이블은 있으나 컬럼이 코드와 다르다
--
--    V31 이 만든 media_kits 는 title/template_style/platforms/demographics/
--    top_content/campaign_results/rate_cards/published_url 형태인데,
--    유일한 소비자인 BrandDealJooqRepository 는 전혀 다른 7개 컬럼을 쓴다.
--    실제 DB 대조로 display_name, categories, social_links, stats_snapshot,
--    rate_card, is_public, slug 가 모두 없음을 확인했다. ALTER 도 없다.
--
--    V31 쪽 형태를 쓰는 코드는 0건이라 충돌 소비자는 없다. 살아 있는 코드를
--    기준으로 스키마를 맞춘다. 기존 컬럼은 데이터 유실을 피하려 남겨 둔다.
-- ---------------------------------------------------------------------------
ALTER TABLE media_kits
    ADD COLUMN IF NOT EXISTS display_name    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS categories      VARCHAR(500) DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS social_links    VARCHAR(500) DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS stats_snapshot  TEXT         DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS rate_card       TEXT         DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS is_public       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS slug            VARCHAR(200);

-- saveMediaKit 은 title 을 채우지 않는다. NOT NULL 이 남아 있으면 컬럼을
-- 추가해도 insert 가 그대로 실패한다. 유일한 소비자가 안 쓰는 컬럼이므로 푼다.
ALTER TABLE media_kits ALTER COLUMN title DROP NOT NULL;

-- findMediaKitByUserId / findMediaKitBySlug 가 둘 다 fetchOne() 이다.
-- 중복 행이 생기면 TooManyRowsException 으로 500 이 되므로 코드가 전제하는
-- 유일성을 스키마로 못 박는다. slug 는 NULL 허용이라 부분 인덱스로 둔다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_media_kits_user
    ON media_kits (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_media_kits_slug
    ON media_kits (slug) WHERE slug IS NOT NULL;

COMMENT ON TABLE brand_deals        IS '브랜드 협찬 딜. BrandDealController 와 수익 분석이 함께 쓴다';
COMMENT ON TABLE trends             IS '수집된 트렌드 키워드 일별 스냅샷';
COMMENT ON TABLE trend_alerts       IS '사용자별 트렌드 키워드 알림 설정';
COMMENT ON TABLE video_translations IS '영상 다국어 메타데이터·자막';
