-- Flyway Migration: V109__video_translation_recovery.sql
--
-- 번역 작업의 **재시작 복구와 출처 보존**.
--
-- ## 무엇이 고객 돈을 잡아먹었나
--
-- `requestTranslation` 은 언어별 비용을 한 번에 차감하고 `TRANSLATING` 행을 커밋한 뒤
-- virtual thread 로 LLM 을 부른다. 그 사이 프로세스가 죽으면:
--
--   1. 행은 영원히 TRANSLATING 이다. 복구 경로가 없다.
--   2. 재요청도 소용없다 — 이미 TRANSLATING 인 언어는 건너뛰므로, 사용자는 다시 눌러도
--      멈춰 있는 행만 돌려받는다. **크레딧은 나갔고 결과는 영영 오지 않는다.**
--   3. 차감 출처(무료 몇, 어느 패키지에서 몇)는 메모리 클로저에만 있어 함께 사라진다.
--      나중에 환불하려 해도 출처를 몰라 구매분이 무료분으로 바뀐다.
--
-- ## 컬럼
--
-- credit_allocation — 이 **언어 한 건**의 차감 출처 분해.
--   {"userId":7,"freeAmount":1,"purchasedAmounts":{"11":2}}
--
--   userId 를 함께 남기는 이유: 복구 경로는 원본 영상이 삭제된 뒤에도 실행되므로
--   videos.user_id 로 소유자를 되짚을 수 없다. 환불 대상을 스냅샷이 직접 들고 가야
--   남의 계정으로 크레딧이 들어가는 사고를 막는다.
--   요청 단위 영수증을 언어별로 쪼개 각 행이 자기 몫만 소유한다. 전체를 모든 행에
--   복사하면 여러 행이 같은 몫을 환불해 없던 크레딧이 생긴다.
--
-- claimed_at — 워커가 이 행을 집은 시각. 원자적 claim 과 stale 판정에 쓴다.
--   NULL 은 아직 아무도 집지 않은 상태다. 조건부 UPDATE 로 DB 가 승자를 정한다 —
--   애플리케이션이 읽고-판단하고-쓰면 두 워커가 같은 행을 동시에 실행한다.
--
-- attempts — 실행 시도 횟수. 상한이 없으면 죽는 입력 하나가 LLM 호출을 무한히 태운다.
--   상한을 넘으면 재실행 대신 **저장된 출처로 환불**하고 FAILED 로 끝낸다.
--   전달하거나 돌려주거나 둘 중 하나이며, 멈춰 있는 상태로 두지 않는다.
--
-- ## 기존 행
--
-- 셋 다 nullable / 기본값 안전값이다. credit_allocation 이 NULL 인 행은 이 마이그레이션
-- 이전에 만들어진 것이며 **자동 환불하지 않는다** — 출처를 모르는 채 무료분으로 돌려주는
-- 것이 바로 이 컬럼이 막으려는 손실이다. 그 건은 수기 정산 로그로 넘긴다.
--
-- ## 안전성
--
-- nullable 컬럼 2 개 + 상수 기본값 컬럼 1 개. PostgreSQL 11+ 는 상수 DEFAULT 도 테이블
-- 재작성 없이 처리한다. 기존 읽기/쓰기 경로는 이 컬럼들을 몰라도 그대로 동작한다.

ALTER TABLE video_translations
    ADD COLUMN IF NOT EXISTS credit_allocation JSONB;

ALTER TABLE video_translations
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP;

ALTER TABLE video_translations
    ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;

-- 복구 스캐너는 "멈춘 TRANSLATING 행"만 찾는다. 전체 스캔을 막는다.
CREATE INDEX IF NOT EXISTS idx_video_translations_stalled
    ON video_translations (claimed_at)
    WHERE status = 'TRANSLATING';

COMMENT ON COLUMN video_translations.credit_allocation IS
    '이 언어 한 건의 차감 출처 분해 {"userId":N,"freeAmount":N,"purchasedAmounts":{"<ai_purchased_credits.id>":N}}. NULL 은 V109 이전 행이며 자동 환불 대상이 아니다';
COMMENT ON COLUMN video_translations.claimed_at IS
    '워커가 이 행을 집은 시각. NULL 은 미점유. 조건부 UPDATE 로 원자적 claim 과 stale 판정에 쓴다';
COMMENT ON COLUMN video_translations.attempts IS
    '실행 시도 횟수. 상한을 넘으면 재실행 대신 저장된 출처로 환불하고 FAILED 로 끝낸다';
