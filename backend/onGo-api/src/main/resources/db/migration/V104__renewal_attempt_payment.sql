-- 갱신 시도와 결제 원장을 잇는다.
--
-- ## 왜 필요한가
--
-- 자동 갱신은 PortOne 에 결제를 만들지만 내부 payments 에는 아무 행도 남기지 않았다.
-- 그래서 세 가지가 동시에 깨졌다.
--
--   1. 고객의 결제 내역에 매달 빠져나간 돈이 보이지 않는다
--   2. PortOne 대시보드와 대조할 내부 기록이 없다
--   3. 갱신 결제 id 가 `sub-…` 라 웹훅 파서(`ongo-` 전용)에 걸려 400 이 된다.
--      특히 **환불 웹훅이 도달하지 못해** 돈을 돌려주고도 구독이 ACTIVE 로 남았다
--
-- payments 행을 만들고 그 id 로 외부 결제 id 를 `ongo-{id}` 로 통일하면 셋이 함께 풀린다.
-- 파서는 손대지 않는다.
--
-- ## nullable 인 이유
--
-- V103 으로 이미 만들어진 행에는 연결할 결제가 없다. NOT NULL 로 만들면 이 마이그레이션이
-- 기존 행에서 실패한다. 값이 없는 행은 "내부 원장 없이 청구된 주기"라는 사실 그대로이며,
-- 애플리케이션이 그것을 재청구하지 않고 운영 확인 대상으로 다룬다.

ALTER TABLE subscription_renewal_attempts
    ADD COLUMN IF NOT EXISTS payment_id BIGINT;

/*
 * 결제 원장을 가리키는 FK. **CASCADE 를 붙이지 않는다.**
 *
 * 결제가 지워졌다고 갱신 이력까지 사라지면 "그 주기에 무슨 일이 있었는지"가 없어진다.
 * ON DELETE 를 명시하지 않아 NO ACTION 이 된다 — V102 self FK 와 같은 이유로 RESTRICT 도
 * 쓰지 않는다(상위 cascade 와 한 문장 안에서 충돌할 수 있다).
 */
ALTER TABLE subscription_renewal_attempts
    DROP CONSTRAINT IF EXISTS fk_renewal_attempts_payment;

ALTER TABLE subscription_renewal_attempts
    ADD CONSTRAINT fk_renewal_attempts_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id);

COMMENT ON COLUMN subscription_renewal_attempts.payment_id IS
    '이 주기의 내부 결제 원장 id. 외부 결제 id 는 ongo-{payment_id} 다. '
    'V103 이전 행은 NULL 이며 재청구하지 않고 운영 확인 대상으로 둔다.';

/*
 * 한 결제는 한 주기에만 속한다.
 *
 * 부분 인덱스인 이유: NULL 인 레거시 행이 여럿이고, 일반 유니크는 NULL 을 서로 다른 값으로
 * 보므로 굳이 포함할 이유가 없다. 조건을 붙여 연결된 행만 대상으로 둔다.
 */
CREATE UNIQUE INDEX IF NOT EXISTS uq_renewal_attempts_payment
    ON subscription_renewal_attempts (payment_id)
    WHERE payment_id IS NOT NULL;
