-- 구독 정기 청구: 빌링키 보관 + 갱신 주기 원장.
--
-- ## 왜 필요한가
--
-- 지금 ACTIVE 구독은 결제 주기가 지나도 아무 일도 일어나지 않는다. next_billing_date 가
-- NULL 로 저장되고(PortOnePaymentService), findDueForBilling 을 부르는 코드가 없어서다.
-- 결과적으로 한 번 결제한 고객이 영구히 유료 플랜을 쓴다.

/*
 * 빌링키는 **암호화해서** 넣는다. 채널 토큰과 같은 AES-256 경로(TokenEncryptionPort)를
 * 쓰며, 평문 컬럼을 만들지 않는다. 이 값 하나로 고객에게 반복 청구가 가능하므로 유출
 * 시 피해가 액세스 토큰보다 크다.
 *
 * 컬럼 이름에 encrypted 를 박아 두는 이유: 나중에 이 값을 로그·응답에 그대로 싣는 코드가
 * 들어오면 이름에서 먼저 걸리게 하려는 것이다.
 */
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS billing_key_encrypted TEXT;

COMMENT ON COLUMN subscriptions.billing_key_encrypted IS
    'PortOne 빌링키(AES-256 암호화). 정기 청구에만 쓰며 응답·로그에 절대 싣지 않는다.';

-- --------------------------------------------------------------------------
-- 갱신 주기 원장
--
-- 스케줄러가 하루 두 번 돌거나 인스턴스가 둘이어도 같은 주기를 두 번 청구하지 않게 한다.
-- 돈이 두 번 빠져나가는 것은 되돌리기 가장 비싼 실수다.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscription_renewal_attempts (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    /*
     * 이 시도가 처리한 주기의 시작 시각. 만료된 주기의 끝(current_period_end)을 그대로
     * 쓴다 — 그 시각이 다음 주기의 시작이다. 이 값이 주기의 신원이다.
     */
    period_start    TIMESTAMP NOT NULL,
    outcome         VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    /*
     * ATTEMPTED         — 이 주기를 선점했고 아직 결과를 모른다. **행을 만들 때의 값**이다.
     *                     청구보다 먼저 자리를 잡아야 이중 청구를 막을 수 있으므로, 결과를
     *                     알기 전에 행이 먼저 생긴다. 프로세스가 청구 도중 죽으면 이 값이
     *                     남는데, 그건 "결과를 모른다"는 사실 그대로다 — 성공이나 실패로
     *                     단정하지 않는다.
     * CHARGED           — 빌링키로 청구에 성공해 기간을 연장했다.
     * CHARGE_FAILED     — 청구를 시도했으나 PG 가 거절했다. 구독은 PAST_DUE 로 내려간다.
     * BILLING_KEY_MISSING — 저장된 결제수단이 없어 **청구를 시도조차 못 했다.**
     *                     CHARGE_FAILED 와 구분한다. 원인이 고객의 카드가 아니라 우리
     *                     쪽 미비이고, 운영자가 할 일도 다르다.
     * NEEDS_REVIEW      — 자동으로 판정할 수 없어 사람이 봐야 한다. PG 는 결제됐다고 하는데
     *                     승인 금액이 다른 경우가 대표적이다. 돈이 이미 움직였으므로 실패로
     *                     내리면 결제한 고객의 권한을 뺏고, 성공으로 잡으면 틀린 금액이
     *                     매출이 된다.
     */
    CONSTRAINT ck_subscription_renewal_attempts_outcome CHECK (
        outcome IN ('ATTEMPTED', 'CHARGED', 'CHARGE_FAILED', 'BILLING_KEY_MISSING', 'NEEDS_REVIEW')
    )
);

/*
 * 한 구독의 한 주기는 한 번만 처리한다.
 *
 * 이 인덱스가 곧 이중 청구 방어선이다. 애플리케이션이 조회 후 삽입하면 인스턴스 둘이
 * 모두 "아직 처리 안 됨"을 보고 통과해 같은 주기를 두 번 청구한다.
 * INSERT ... ON CONFLICT 의 판정자로 쓴다.
 */
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_renewal_attempts_period
    ON subscription_renewal_attempts (subscription_id, period_start);

COMMENT ON TABLE subscription_renewal_attempts IS
    '구독 갱신 주기별 처리 원장. (subscription_id, period_start) 가 주기의 신원이며 '
    '같은 주기를 두 번 청구하지 않도록 막는다.';
