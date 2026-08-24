-- 유료 쇼츠 파일럿 단위경제: 운영자가 확인한 매출과 외부 인프라 원가.
--
-- 이 값들은 **운영자가 손으로 적은 것**이다. PortOne 결제 내역이나 AI/R2 청구서와
-- 연동돼 있지 않다. 자동 연동을 시사하는 이름을 쓰면 나중에 그 숫자로 원가를 단정하게
-- 되므로, 컬럼·이벤트·응답 필드 어디에도 "실제 청구액"이라고 쓰지 않는다.
--
-- 사람 투입 시간(operator_minutes, V98)과 축이 다르므로 컬럼을 나눈다. 한 컬럼에 섞으면
-- 분과 원이 같은 자리에 들어가 합계가 조용히 오염된다.

ALTER TABLE ugc_shorts_pilot_events
    ADD COLUMN IF NOT EXISTS amount_krw BIGINT;

COMMENT ON COLUMN ugc_shorts_pilot_events.amount_krw IS
    'OPERATOR_REVENUE_LOGGED / OPERATOR_EXTERNAL_COST_LOGGED 전용. 원 단위이며 운영자가 '
    '직접 확인해 입력한 값이다. 결제·청구 시스템과 연동된 값이 아니다.';

-- 기존 CHECK 재정의로 새 이벤트 타입을 추가한다.
-- V97/V98 이 만든 네 타입은 그대로 유지되므로 기존 행은 전부 유효하다.
ALTER TABLE ugc_shorts_pilot_events
    DROP CONSTRAINT IF EXISTS ck_shorts_pilot_events_type;

ALTER TABLE ugc_shorts_pilot_events
    ADD CONSTRAINT ck_shorts_pilot_events_type CHECK (
        event_type IN (
            'PILOT_ENROLLED',
            'STAGE_RERUN',
            'RENDER_ATTEMPT_FAILED',
            'OPERATOR_TIME_LOGGED',
            'OPERATOR_REVENUE_LOGGED',
            'OPERATOR_EXTERNAL_COST_LOGGED'
        )
    );

/*
 * 금액은 두 이벤트에만 붙는다.
 *
 * 하한 1원: 0 은 "무상 제공"과 "기록을 깜빡했다"를 구분하지 못해 합계를 오염시킨다.
 * 무상 건은 아예 적지 않고 리포트에서 미기록(null)으로 남는 편이 정직하다.
 *
 * 상한 1억원: 오타 방어다. 파일럿 한 건이 이 금액을 넘길 일은 없고, 넘긴다면 자릿수를
 * 잘못 친 것이다. 한 번 들어간 값은 append-only 라 지울 수 없으므로 입구에서 막는다.
 *
 * 다른 이벤트에 금액이 붙으면 합계가 조용히 부풀어 오르므로 NULL 만 허용한다.
 * V98 이전 행은 amount_krw 가 NULL 이라 이 조건을 그대로 만족한다.
 *
 * V98 의 operator_minutes CHECK 는 수정하지 않는다 — 신규 두 타입은
 * `event_type <> 'OPERATOR_TIME_LOGGED'` 쪽 분기에 걸리고 minutes 가 NULL 이므로 통과한다.
 */
ALTER TABLE ugc_shorts_pilot_events
    DROP CONSTRAINT IF EXISTS ck_shorts_pilot_events_amount_krw;

ALTER TABLE ugc_shorts_pilot_events
    ADD CONSTRAINT ck_shorts_pilot_events_amount_krw CHECK (
        (
            event_type IN ('OPERATOR_REVENUE_LOGGED', 'OPERATOR_EXTERNAL_COST_LOGGED')
            AND amount_krw BETWEEN 1 AND 100000000
        )
        OR (
            event_type NOT IN ('OPERATOR_REVENUE_LOGGED', 'OPERATOR_EXTERNAL_COST_LOGGED')
            AND amount_krw IS NULL
        )
    );
