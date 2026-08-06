-- 코드가 쓰고 있는데 마이그레이션에 없던 테이블을 만든다.
--
-- CompetitorJooqRepository 가 competitor_analytics_daily 를 조회(:103,:116)하고
-- upsert(:125-143) 하는데, V1~V60 어디에도 CREATE TABLE 이 없다.
-- 실제 PostgreSQL 16 에 전체 마이그레이션을 적용해 확인했다(존재 0, 대조군 competitors 1).
--
-- 그동안 드러나지 않은 이유:
--   CompetitorSyncScheduler 가 competitor 스냅샷을 먼저 갱신해 커밋하고(:33-39),
--   이어서 upsertAnalytics 를 호출하는데(:42-49) 그 예외를 :52-54 에서 삼킨다.
--   따라서 테이블이 없으면 relation does not exist 가 매번 조용히 묻히고
--   스냅샷만 갱신된 채 일별 분석이 영구 누락된다.
--   이 경로를 지나는 테스트가 없어 CI 도 잡지 못했다.
--
-- 운영 DB 에 수동 생성된 테이블이 있을 수 있어 IF NOT EXISTS 로 둔다.
-- 컬럼과 제약은 CompetitorJooqRepository 의 실제 사용(upsert 컬럼 8개,
-- onConflict(competitor_id, date), toCompetitorAnalytics 의 id/created_at 읽기)에서 도출했다.

CREATE TABLE IF NOT EXISTS competitor_analytics_daily (
    id               BIGSERIAL PRIMARY KEY,
    competitor_id    BIGINT NOT NULL REFERENCES competitors(id) ON DELETE CASCADE,
    date             DATE NOT NULL,
    subscriber_count BIGINT  DEFAULT 0,
    video_count      INTEGER DEFAULT 0,
    avg_views        BIGINT  DEFAULT 0,
    avg_likes        BIGINT  DEFAULT 0,
    avg_comments     BIGINT  DEFAULT 0,
    total_views      BIGINT  DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),

    -- upsertAnalytics 의 onConflict(competitor_id, date) 가 이 제약을 요구한다.
    -- 이 제약이 만드는 (competitor_id, date) btree 가 조회 인덱스도 겸한다.
    -- findAnalyticsByCompetitorId(s)AndDateRange 는 competitor_id 등호/IN +
    -- date 범위 + ORDER BY date ASC 라서 이 인덱스로 정확히 커버된다.
    -- 별도 인덱스를 두면 쓰기 비용만 늘어 추가하지 않았다.
    CONSTRAINT uq_competitor_analytics_daily UNIQUE (competitor_id, date)
);

COMMENT ON TABLE competitor_analytics_daily IS '경쟁 채널의 일별 지표 스냅샷. CompetitorSyncScheduler 가 매일 upsert 한다';
