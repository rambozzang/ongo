-- Flyway Migration: V110__ugc_metric_snapshot_provenance.sql
--
-- UGC 지표 스냅샷의 **출처와 미측정 지표**를 남긴다.
--
-- ## 무엇이 브랜드 성과를 왜곡했나
--
-- Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다.
-- 그런데 동기화 스케줄러는 그 자리에 0 을 넣어 저장했고, 캠페인 분석은 플랫폼 구분 없이
-- 합산했다. 그 합계가 브랜드 성과와 **보상 판단** 화면에 그대로 올라간다.
--
-- Facebook 중심 캠페인은 공유 성과가 구조적으로 0 으로 보고됐다 — 실제로 공유가 없어서가
-- 아니라 물어보지 않았기 때문인데, 화면은 둘을 구분하지 못했다.
--
-- ## 왜 플랫폼 이름으로 거르지 않는가
--
-- 운영자가 손으로 넣는 백필(`POST .../campaign-posts/{id}/metrics`)이 있다. 플랫폼만 보고
-- 값을 버리면 **Facebook 게시물의 수동 입력 공유 수까지 함께 사라진다.** 가용성은
-- 플랫폼이 아니라 **그 스냅샷이 어떻게 만들어졌는가**의 문제다. 그래서 행에 남긴다.
--
-- ## 컬럼
--
-- source — 'PLATFORM_SYNC' | 'MANUAL'.
--   PLATFORM_SYNC 의 0 은 unavailable_metrics 에 없을 때만 측정값이다.
--   MANUAL 의 0 은 사람이 0 이라고 적은 것이므로 언제나 측정값이다.
--
-- unavailable_metrics — 이 스냅샷이 측정하지 못한 지표 이름 배열.
--   ["shares"] 처럼 저장한다. 합산·비교에서 제외할 대상이다.
--
-- ## 기존 행
--
-- 둘 다 nullable 이다. `source IS NULL` 은 V110 이전 행이며 출처를 알 수 없다 —
-- 그 시절에는 동기화와 수동 입력이 같은 모양으로 저장돼 구분할 수단이 없다.
--
-- 애플리케이션은 그런 행을 UNKNOWN 으로 읽고 **0 만 미가용으로 판정한다.**
-- 0 이 아닌 값은 누군가 실제로 관측한 것이 분명하므로 그대로 살린다. 전부 버리면
-- 과거 캠페인 성과가 통째로 사라지고, 전부 믿으면 지금 고치려는 왜곡이 남는다.
--
-- 기본값을 채우지 않는 이유도 같다. 'MANUAL' 로 채우면 미수집 0 이 "사람이 적은 0" 으로
-- 둔갑하고, 'PLATFORM_SYNC' 로 채우면 수동 백필이 미수집으로 오인된다.
--
-- ## 안전성
--
-- nullable 컬럼 2 개 추가이며 기본값 채우기가 없다. PostgreSQL 11+ 에서 테이블 재작성
-- 없이 즉시 완료된다. 기존 읽기/쓰기 경로는 이 컬럼을 몰라도 그대로 동작한다.

ALTER TABLE ugc_post_metric_snapshots
    ADD COLUMN IF NOT EXISTS source VARCHAR(20);

ALTER TABLE ugc_post_metric_snapshots
    ADD COLUMN IF NOT EXISTS unavailable_metrics JSONB;

ALTER TABLE ugc_post_metric_snapshots
    DROP CONSTRAINT IF EXISTS chk_ugc_metric_snapshot_source;

-- 알 수 없는 출처 문자열이 들어오면 애플리케이션이 UNKNOWN 으로 강등해 0 을 버린다.
-- 오타 하나가 조용히 성과를 지우지 않도록 값 자체를 제한한다.
ALTER TABLE ugc_post_metric_snapshots
    ADD CONSTRAINT chk_ugc_metric_snapshot_source
    CHECK (source IS NULL OR source IN ('PLATFORM_SYNC', 'MANUAL'));

COMMENT ON COLUMN ugc_post_metric_snapshots.source IS
    '숫자의 출처. PLATFORM_SYNC | MANUAL. NULL 은 V110 이전 행이며 0 만 미측정으로 본다';
COMMENT ON COLUMN ugc_post_metric_snapshots.unavailable_metrics IS
    '이 스냅샷이 측정하지 못한 지표 이름 배열 ["shares"]. 합산·비교에서 제외한다';
