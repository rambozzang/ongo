-- Flyway Migration: V111__shorts_stage_credit_allocation.sql
--
-- 쇼츠 단계 차감의 **출처 분해와 정산 표식**을 남긴다.
--
-- ## 왜 필요한가
--
-- 쇼츠 파이프라인은 단계마다 크레딧을 차감하고, 그 단계가 실패하면 차감한 만큼 되돌린다.
-- 되돌릴 근거는 차감 시점에 만든 인메모리 영수증(`CreditAllocation`)이다.
--
-- 그런데 오케스트레이터는 트랜잭션 밖에서 외부 AI 를 호출한다. 그 사이 프로세스가 죽으면
-- **차감은 이미 커밋돼 있고 영수증은 프로세스와 함께 사라진다.** 사용자는 돈을 냈고 결과는
-- 없으며, 되돌릴 방법도 없다. 이 배포에는 감시자가 없어 크래시 후 수동 재기동까지 시간이
-- 걸리므로 노출도가 더 크다.
--
-- 분해를 여기 남기면 다른 프로세스가 정확히 같은 자리로 되돌릴 수 있다.
--
-- ## 왜 금액만으로는 안 되는가
--
-- 차감은 무료분을 먼저 쓰고 모자라면 구매 패키지를 만료 임박순으로 가져간다. 출처를 모른 채
-- 총액을 무료분에 얹으면 **고객 자산이 손실된다.**
--
--   * 구매분에서 나간 크레딧이 월말에 사라지는 무료분으로 바뀐다.
--   * free_monthly 한도에 걸린 몫은 어디에도 복구되지 않고 그대로 증발한다.
--
-- 같은 이유로 `ai_pipeline_jobs` 가 V108 에서 이미 이 분해를 남기고 있다. 형식을 그대로 쓴다.
--
-- ## 형식
--
--   {"freeAmount": 2, "purchasedAmounts": {"11": 3, "12": 5}}
--
-- 키는 ai_purchased_credits.id 다. 만료일이 다른 패키지 사이에서 유효기간이 바뀌지 않도록
-- 패키지별로 남긴다.
--
-- ## 기존 행
--
-- NULL 을 허용하고 기본값을 두지 않는다. 이 마이그레이션 이전 단계는 분해를 알 수 없고,
-- 그 행은 **자동 환불하지 않는다** — 출처를 모르는 채 무료분으로 돌려주는 것이 바로 이
-- 컬럼이 막으려는 손실이다. `'{}'::jsonb` 같은 기본값을 넣으면 "분해가 없다" 와 "분해가
-- 비어 있다" 가 구분되지 않아 레거시 행이 정상 행처럼 보인다.
--
-- ## refunded_credits — 정산 표식
--
-- 0 은 "아직 정산하지 않음"이다. 정산은 표식과 실제 환불을 **한 트랜잭션**에서 처리하며,
-- 조건부 갱신(`WHERE refunded_credits = 0`)으로 승자를 DB 가 정한다. 애플리케이션이
-- 읽고-판단하고-쓰면 동시에 들어온 두 정산이 모두 통과해 두 번 환불된다.
--
-- 환불이 실패하면 트랜잭션이 통째로 롤백되어 표식도 0 으로 돌아간다. 그래서 다음 시도가
-- 같은 행을 다시 집을 수 있다 — 정산은 재시도 가능해야 한다.
--
-- ## 안전성
--
-- nullable 컬럼 1개 + DEFAULT 0 컬럼 1개 추가이며 기존 행 재작성이 없다. PostgreSQL 11+
-- 에서 즉시 완료된다. CHECK 는 기존 행에도 성립한다 — 모든 기존 행은 refunded_credits 가
-- 기본값 0 이고 credit_cost 는 NOT NULL DEFAULT 0 이므로 `0 <= credit_cost` 가 참이다.
--
-- ADD CONSTRAINT 에는 IF NOT EXISTS 가 없으므로 DROP ... IF EXISTS 를 선행해 재실행을
-- 안전하게 만든다(V107 에서 확립한 관용구).

ALTER TABLE ugc_shorts_run_stages
    ADD COLUMN IF NOT EXISTS credit_allocation JSONB;

ALTER TABLE ugc_shorts_run_stages
    ADD COLUMN IF NOT EXISTS refunded_credits INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ugc_shorts_run_stages
    DROP CONSTRAINT IF EXISTS chk_ugc_shorts_run_stages_refunded;

-- 환불이 차감액을 넘을 수 없다. 넘는 순간 원장은 우리가 받은 적 없는 돈을 돌려준 것이 된다.
ALTER TABLE ugc_shorts_run_stages
    ADD CONSTRAINT chk_ugc_shorts_run_stages_refunded
    CHECK (refunded_credits >= 0 AND refunded_credits <= credit_cost);

COMMENT ON COLUMN ugc_shorts_run_stages.credit_allocation IS
    '차감 출처 분해 {"freeAmount":N,"purchasedAmounts":{"<ai_purchased_credits.id>":N}}. NULL 은 V111 이전 행이며 자동 환불 대상이 아니다';

COMMENT ON COLUMN ugc_shorts_run_stages.refunded_credits IS
    '이미 환불한 크레딧. 0보다 크면 정산이 끝난 것이며 다시 환불하지 않는다';
