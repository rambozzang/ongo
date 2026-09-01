-- --------------------------------------------------------------------------
-- analytics_daily: 광고 수익의 통화와 측정 상태
--
-- ## 왜 필요한가
--
-- revenue_micro 는 처음부터 `NOT NULL DEFAULT 0` 이라 "실제로 0 원을 번 날" 과
-- "아직 물어보지 못한 날" 이 같은 값으로 저장된다. 그래서 수집을 시작해도 화면은
-- 둘을 구분할 수 없고, 수집하지 않는 플랫폼의 0 까지 측정값처럼 합산된다.
--
-- revenue_currency 도 마찬가지다. YouTube estimatedRevenue 는 채널의 지급 통화로
-- 내려오는데 V1 의 컬럼 주석은 "1원 = 1,000,000 micro" 를 전제한다. 통화를 잃은
-- 숫자를 원화로 읽으면 몇백 배 틀린다.
--
-- ## 기존 행
--
-- 지금까지 저장된 행은 어떤 수익도 조회한 적이 없다. 그래서 기본값이 UNSUPPORTED 다 —
-- "0 원을 벌었다" 가 아니라 "측정한 적 없다" 가 사실이다.
--
-- ## 파티션
--
-- analytics_daily 는 date 기준 RANGE 파티션이다. 부모에 컬럼을 추가하면 기존 월별
-- 파티션에도 함께 적용된다(V53 과 같은 방식).
--
-- ## 배포 주의
--
-- **운영 Flyway 는 V93 이고 V94~V106 이 아직 적용되지 않았다(2026-08-27 확인).**
-- 이 마이그레이션은 그 뒤에 붙으므로 V94~V106 을 먼저 적용하지 않으면 운영에
-- 반영되지 않는다. deploy/preflight-schema.sh 가 V107 과 이 컬럼들을 검사해
-- 미적용 상태의 배포를 차단한다.
-- ## 락 대기 상한
--
-- 아래 문장은 부모와 **모든 월별 파티션**에 ACCESS EXCLUSIVE 를 잡는다. 그리고 이
-- 마이그레이션은 **구 서비스가 살아 있는 동안** 돈다(deploy/migrate-schema.sh).
-- 그래서 롱 리드 하나 뒤에 줄을 서면 그 뒤의 모든 조회·수집이 함께 막힌다.
--
-- deploy/migrate-schema.sh 의 MIGRATE_TIMEOUT_SECONDS 로는 이 상황을 끊을 수 없다.
-- 그건 **클라이언트 측** 제한이라 JVM 을 죽여도 PostgreSQL 백엔드는 스캔 중
-- 클라이언트 단절을 감지하지 않는다 — ALTER 는 서버에서 계속 돌고 락도 계속 쥔다.
-- 락을 실제로 놓게 만들 수 있는 것은 **서버 측 상한**뿐이다.
--
-- 초과하면 이 마이그레이션만 실패한다. deploy.sh 가 migrate-schema.sh 실패 시
-- 배포를 중단하므로 기존 서비스는 그대로 살아 있다(무중단 실패). 한산한 시간에
-- 다시 시도하는 편이, 운영을 붙잡은 채 끝나기를 기다리는 것보다 언제나 낫다.
--
-- SET LOCAL 이라 이 트랜잭션에서만 유효하다. 이 파일은 전 문장이 트랜잭션이므로
-- Flyway 가 하나의 트랜잭션으로 감싸고, 끝나면 세션 기본값으로 돌아간다.
SET LOCAL lock_timeout = '5s';

-- --------------------------------------------------------------------------
ALTER TABLE analytics_daily
    ADD COLUMN IF NOT EXISTS revenue_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS revenue_status   VARCHAR(20) NOT NULL DEFAULT 'UNSUPPORTED';

-- 상태 문자열은 코드의 RevenueStatus 와 1:1 이다. 오타로 새 값이 새어 들어오면
-- 화면이 그 행을 어느 쪽으로도 해석하지 못한다.
--
-- DROP IF EXISTS 로 먼저 지운다(V98/V99/V101/V102/V104/V106/V110 과 같은 관례).
-- PostgreSQL 의 ADD CONSTRAINT 에는 IF NOT EXISTS 가 없어서, 선행 DROP 이 없으면
-- 이 파일은 **한 번만** 적용할 수 있다. 락 상한에 걸려 중단된 뒤 사람이 손으로
-- 일부를 먼저 넣어 둔 상태에서 재시도하면 "이미 존재한다"로 막힌다.
ALTER TABLE analytics_daily
    DROP CONSTRAINT IF EXISTS chk_analytics_revenue_status;

ALTER TABLE analytics_daily
    ADD CONSTRAINT chk_analytics_revenue_status
    CHECK (revenue_status IN ('MEASURED', 'PENDING', 'PERMISSION_REQUIRED', 'UNSUPPORTED', 'ERROR'));

-- 통화 없는 측정값은 저장할 수 없다. 측정하지 않은 행은 통화를 가질 수 없다.
ALTER TABLE analytics_daily
    DROP CONSTRAINT IF EXISTS chk_analytics_revenue_currency;

ALTER TABLE analytics_daily
    ADD CONSTRAINT chk_analytics_revenue_currency
    CHECK (
        (revenue_status = 'MEASURED' AND revenue_currency IS NOT NULL)
        OR (revenue_status <> 'MEASURED' AND revenue_currency IS NULL)
    );

CREATE INDEX IF NOT EXISTS idx_analytics_daily_revenue_status
    ON analytics_daily (revenue_status);

COMMENT ON COLUMN analytics_daily.revenue_currency IS '광고 수익 통화 (ISO 4217). MEASURED 일 때만 존재';
COMMENT ON COLUMN analytics_daily.revenue_status IS '광고 수익 측정 상태: MEASURED/PENDING/PERMISSION_REQUIRED/UNSUPPORTED/ERROR';
COMMENT ON COLUMN analytics_daily.revenue_micro IS '광고 수익 (revenue_currency 기준 마이크로 단위: 1단위 = 1,000,000 micro). revenue_status=MEASURED 일 때만 의미가 있다';
