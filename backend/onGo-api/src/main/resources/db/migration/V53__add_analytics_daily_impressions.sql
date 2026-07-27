-- --------------------------------------------------------------------------
-- analytics_daily: 노출수 / 평균 시청 시간 컬럼 추가
--
-- AnalyticsDaily 도메인과 AnalyticsJooqRepository.upsert 는 처음부터 이 두 컬럼을
-- 읽고 썼지만 테이블에는 존재하지 않았다. 그래서 6시간마다 도는 성과 수집
-- (AnalyticsSyncScheduler)이 매 실행마다 SQL 오류로 실패했고, analytics_daily 는
-- 계속 비어 있어 대시보드의 모든 성과 지표가 0 으로 표시됐다.
--
-- 파티션 부모 테이블에 컬럼을 추가하면 기존 월별 파티션에도 함께 적용된다.
-- --------------------------------------------------------------------------
ALTER TABLE analytics_daily
    ADD COLUMN IF NOT EXISTS impressions               INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS avg_view_duration_seconds INTEGER NOT NULL DEFAULT 0;

ALTER TABLE analytics_daily
    ADD CONSTRAINT chk_analytics_impressions CHECK (impressions >= 0);

ALTER TABLE analytics_daily
    ADD CONSTRAINT chk_analytics_avg_view_duration CHECK (avg_view_duration_seconds >= 0);

COMMENT ON COLUMN analytics_daily.impressions IS '노출수 (썸네일이 사용자에게 표시된 횟수)';
COMMENT ON COLUMN analytics_daily.avg_view_duration_seconds IS '평균 시청 시간 (초)';
