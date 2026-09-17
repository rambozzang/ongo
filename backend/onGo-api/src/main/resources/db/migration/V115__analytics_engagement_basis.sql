-- --------------------------------------------------------------------------
-- analytics_daily: 참여 지표가 "그 날의 증분" 인지 "평생 누적" 인지 구분한다
--
-- ## 왜 필요한가
--
-- 스케줄러는 어댑터가 준 숫자를 그 날짜 행에 그대로 넣고, 화면은 그 행들을 기간으로
-- SUM 한다. 이 조합은 저장된 값이 **그 날 하루의 증가분**일 때만 맞다.
--
-- 그런데 어댑터 13개 중 YouTube 하나만 그 조건을 만족한다. 나머지 12개는 날짜를
-- 받기만 하고 평생 누적 카운터를 돌려준다(TikTok view_count, Instagram plays,
-- Dailymotion views_total, Pinterest lifetime_metrics ...).
--
-- 결과: 30일 창이면 조회수가 약 30배로 나온다. 크리에이터가 처음 보는 숫자다.
--
-- ## 기존 행을 어떻게 하는가
--
-- **지우지 않는다. 다시 라벨링한다.**
--
-- 누적 플랫폼 행에 들어 있는 숫자는 쓰레기가 아니라 **관측 시점의 평생 누적 스냅샷**
-- 이다. 우리가 그것을 "그 날의 증분" 이라고 잘못 부르고 있었을 뿐이다. 그래서 값은
-- *_total 스냅샷 칸으로 옮기고, 증분 칸은 0 으로 남기며, basis 를 LEGACY_CUMULATIVE
-- 로 표시해 합계에서 뺀다.
--
-- 차분으로 과거를 복원하지 않는다. 백필 루프가 **오늘의 누적값을 과거 날짜들에**
-- 써 넣었기 때문에, 그 행들의 차분은 29일이 0 이고 하루만 거대한 값이 된다.
-- 그것은 복원이 아니라 날조다 — "미측정과 실측 0 을 구분한다" 는 원칙에 반한다.
--
-- ## 왜 기본값이 LEGACY_CUMULATIVE 인가 (fail-closed)
--
-- 라벨이 없는 행은 합계에서 빠진다. 반대로 INCREMENTAL 을 기본값으로 두면, 누군가
-- basis 설정을 빠뜨렸을 때 그 값이 조용히 합계에 섞여 다시 숫자를 부풀린다.
-- 빠지는 쪽은 눈에 띄고, 섞이는 쪽은 눈에 띄지 않는다.
--
-- ## 파티션
--
-- analytics_daily 는 date 기준 RANGE 파티션이다. 부모에 컬럼을 추가하면 기존 월별
-- 파티션에도 함께 적용된다(V53·V107 과 같은 방식).
--
-- ## 락 대기 상한
--
-- 아래 ALTER 는 부모와 모든 월별 파티션에 ACCESS EXCLUSIVE 를 잡고, 이 마이그레이션은
-- 구 서비스가 살아 있는 동안 돈다(deploy/migrate-schema.sh). 롱 리드 뒤에 줄을 서면
-- 그 뒤의 모든 조회·수집이 함께 막힌다. 클라이언트 타임아웃으로는 끊을 수 없으므로
-- (JVM 을 죽여도 서버의 ALTER 는 계속 돈다) 서버 측 상한을 건다.
--
-- 초과하면 이 마이그레이션만 실패하고 deploy.sh 가 배포를 중단한다 — 기존 서비스는
-- 그대로 살아 있다(무중단 실패).
SET LOCAL lock_timeout = '5s';

-- --------------------------------------------------------------------------
-- 1. 스냅샷 칸과 basis 라벨
--
-- *_total 은 관측 시점의 평생 누적값이다. 다음 주기의 차분을 구하려면 이 값이 필요하다.
-- 증분 플랫폼(YouTube)은 누적을 받지 않으므로 NULL 로 남는다.
ALTER TABLE analytics_daily
    ADD COLUMN IF NOT EXISTS views_total       BIGINT,
    ADD COLUMN IF NOT EXISTS likes_total       BIGINT,
    ADD COLUMN IF NOT EXISTS comments_total    BIGINT,
    ADD COLUMN IF NOT EXISTS shares_total      BIGINT,
    ADD COLUMN IF NOT EXISTS engagement_basis  VARCHAR(20) NOT NULL DEFAULT 'LEGACY_CUMULATIVE';

-- PostgreSQL 의 ADD CONSTRAINT 에는 IF NOT EXISTS 가 없다. 선행 DROP 이 없으면 이
-- 파일은 한 번만 적용할 수 있고, 락 상한에 걸려 중단된 뒤 재시도할 수 없다
-- (V98/V99/V101/V102/V104/V106/V107/V110 과 같은 관례).
ALTER TABLE analytics_daily
    DROP CONSTRAINT IF EXISTS chk_analytics_engagement_basis;

ALTER TABLE analytics_daily
    ADD CONSTRAINT chk_analytics_engagement_basis
    CHECK (engagement_basis IN ('INCREMENTAL', 'BASELINE', 'LEGACY_CUMULATIVE'));

-- 스냅샷은 줄어들 수 없다(평생 누적 카운터). 음수가 들어오면 차분 계산이 망가진다.
ALTER TABLE analytics_daily
    DROP CONSTRAINT IF EXISTS chk_analytics_totals_non_negative;

ALTER TABLE analytics_daily
    ADD CONSTRAINT chk_analytics_totals_non_negative
    CHECK (
        (views_total    IS NULL OR views_total    >= 0)
        AND (likes_total    IS NULL OR likes_total    >= 0)
        AND (comments_total IS NULL OR comments_total >= 0)
        AND (shares_total   IS NULL OR shares_total   >= 0)
    );

-- --------------------------------------------------------------------------
-- 2. 기존 행 라벨링
--
-- analytics_daily 에는 platform 컬럼이 없다. video_uploads 를 통해야 한다.
--
-- YouTube 행만 INCREMENTAL 이다. 그 값들은 처음부터 Analytics API 의 기간값이라
-- 지금도 SUM 해서 맞다.
UPDATE analytics_daily ad
SET engagement_basis = 'INCREMENTAL'
FROM video_uploads vu
WHERE vu.id = ad.video_upload_id
  AND vu.platform = 'YOUTUBE'
  AND ad.engagement_basis <> 'INCREMENTAL';

-- 나머지(누적 플랫폼) 행은 값을 스냅샷 칸으로 옮기고 증분 칸을 0 으로 만든다.
-- basis 는 기본값 LEGACY_CUMULATIVE 그대로라 합계에서 빠진다.
--
-- 0 으로 두는 이유: 이 컬럼들은 NOT NULL 이고 CHECK (>= 0) 이 걸려 있다. NULL 로
-- 바꾸려면 제약과 파티션 전체를 건드려야 하는데, basis 로 이미 합계에서 빠지므로
-- 값의 형태를 바꿀 필요가 없다. **판정은 basis 가 한다.**
UPDATE analytics_daily ad
SET views_total    = COALESCE(ad.views_total,    ad.views),
    likes_total    = COALESCE(ad.likes_total,    ad.likes),
    comments_total = COALESCE(ad.comments_total, ad.comments_count),
    shares_total   = COALESCE(ad.shares_total,   ad.shares),
    views          = 0,
    likes          = 0,
    comments_count = 0,
    shares         = 0
FROM video_uploads vu
WHERE vu.id = ad.video_upload_id
  AND vu.platform <> 'YOUTUBE'
  AND ad.engagement_basis = 'LEGACY_CUMULATIVE';

-- --------------------------------------------------------------------------
-- 3. 인덱스와 주석
--
-- 모든 참여 지표 집계가 이 컬럼으로 거르므로 인덱스를 둔다(revenue_status 와 같은 이유).
CREATE INDEX IF NOT EXISTS idx_analytics_daily_engagement_basis
    ON analytics_daily (engagement_basis);

COMMENT ON COLUMN analytics_daily.engagement_basis IS
    '참여 지표의 의미: INCREMENTAL=그 날의 증분(SUM 가능) / BASELINE=누적 첫 관측이라 증분 산출 불가 / LEGACY_CUMULATIVE=차분 도입 이전의 누적 스냅샷. INCREMENTAL 만 합산한다';
COMMENT ON COLUMN analytics_daily.views_total IS
    '관측 시점의 평생 누적 조회수. 다음 주기의 증분을 구하는 기준선이다. 증분 플랫폼(YouTube)은 NULL';
COMMENT ON COLUMN analytics_daily.likes_total IS '관측 시점의 평생 누적 좋아요. views_total 과 같은 규칙';
COMMENT ON COLUMN analytics_daily.comments_total IS '관측 시점의 평생 누적 댓글. views_total 과 같은 규칙';
COMMENT ON COLUMN analytics_daily.shares_total IS '관측 시점의 평생 누적 공유. views_total 과 같은 규칙';
