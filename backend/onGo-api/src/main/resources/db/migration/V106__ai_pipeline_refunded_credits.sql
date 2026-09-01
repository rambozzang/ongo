-- Flyway Migration: V106__ai_pipeline_refunded_credits.sql
--
-- 파이프라인 환불의 멱등 표식.
--
-- 예전에는 취소 경로가 크레딧을 먼저 환불하고(refundCredit) 그 다음에 상태를 저장했다.
-- 저장이 실패하면 상태가 CANCELLED 가 아니므로 "이미 취소됨" 가드를 다시 통과해, 같은
-- 파이프라인을 두 번 환불할 수 있었다. 자연 실패에도 환불을 붙이면 실행 스레드와 취소
-- 요청이 동시에 같은 금액을 돌려주는 경합이 생긴다.
--
-- 환불 여부를 DB 에 남기고 조건부 갱신(`WHERE refunded_credits = 0`)으로 승자를 정한다.
-- 애플리케이션이 읽고-판단하고-쓰면 두 요청이 모두 통과하지만, 이 조건은 DB 가 판정한다.
--
-- 0 은 "아직 환불하지 않음"이다. 환불액이 0원인 경우(전 스텝 소비)도 정산은 끝났으므로
-- 별도 표식이 필요하지만, 그때는 돌려줄 돈이 없어 두 번 실행돼도 무해하다.

ALTER TABLE ai_pipeline_jobs
    ADD COLUMN IF NOT EXISTS refunded_credits INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ai_pipeline_jobs
    DROP CONSTRAINT IF EXISTS chk_ai_pipeline_jobs_refunded_credits;

-- 환불이 차감액을 넘을 수 없다. 넘는 순간 원장은 우리가 받은 적 없는 돈을 돌려준 것이 된다.
ALTER TABLE ai_pipeline_jobs
    ADD CONSTRAINT chk_ai_pipeline_jobs_refunded_credits
    CHECK (refunded_credits >= 0 AND refunded_credits <= total_credits_charged);

COMMENT ON COLUMN ai_pipeline_jobs.refunded_credits IS
    '이미 환불한 크레딧. 0보다 크면 정산이 끝난 것이며 다시 환불하지 않는다';
