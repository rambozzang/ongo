-- 유료 쇼츠 파일럿 수기 기록의 역분개(reversal).
--
-- ## 왜 필요한가
--
-- 매출·외부원가·투입시간은 운영자가 손으로 적는다. 300,000 을 3,000,000 으로 잘못 치면
-- 지금은 고칠 방법이 전혀 없다. update/delete 경로가 없고, 음수로 상쇄하려 해도 API 와
-- ck_shorts_pilot_events_amount_krw 가 하한 1 을 강제한다. 그래서 오입력이 영구히
-- "그럴듯한 숫자"로 합계에 참여한다. 틀린 값은 미기록(null)보다 나쁘다.
--
-- ## 왜 지우지 않고 행을 더 쌓는가
--
-- 원본을 지우거나 덮어쓰면 "무엇을 잘못 적었었는지"가 사라진다. 무효화 사실 자체를 새
-- 행으로 남기면 감사 추적이 줄지 않고 오히려 늘어난다 — 누가 언제 무엇을 취소했는지까지
-- 남는다. 이 테이블의 append-only 원칙은 그대로다.
--
-- ## 이 마이그레이션이 하지 않는 것
--
-- 금액을 결제 시스템과 대조하지 않는다. 역분개는 수기 입력을 고칠 수 있게 할 뿐이며,
-- REVENUE_AND_COST_ARE_OPERATOR_REPORTED 한계는 그대로 유지된다.

ALTER TABLE ugc_shorts_pilot_events
    ADD COLUMN IF NOT EXISTS reverses_event_id BIGINT;

/*
 * 자기 참조 FK — **NO ACTION(ON DELETE 생략)이다. RESTRICT 가 아니다.**
 *
 * self FK 는 NO ACTION(ON DELETE 생략)으로, 기존 실행 삭제 시 run_id cascade 가 같은
 * 문장에서 원본·역분개 행을 함께 제거하는 현재 삭제/개인정보 정책을 보존한다.
 * self FK 자체는 cascade 하지 않는다.
 *
 * ## 왜 RESTRICT 가 아닌가
 *
 * run_id FK 에 ON DELETE CASCADE 가 걸려 있다(V97). 고객이 실행을 지우면 그 실행의 모든
 * 이벤트가 **한 문장 안에서** 함께 지워진다. RESTRICT 는 참조 무결성을 행 단위로 **즉시**
 * 검사하므로, 역분개 행이 아직 남아 있는 시점에 원본 삭제를 막아 실행 삭제 자체가
 * 실패한다 — 무효화 이력이 있는 실행을 고객이 영영 지우지 못하게 되고, 그건 계정·데이터
 * 삭제 정책과 정면으로 부딪친다.
 *
 * NO ACTION 은 문장이 끝난 뒤 검사하므로 원본과 역분개 행이 함께 사라진 상태를 보고
 * 통과한다. 둘 다 "cascade 하지 않는다"는 같지만, 여기서는 NO ACTION 만이 기존 삭제
 * 경로를 깨지 않는다.
 *
 * ## 이 선택이 포기하지 않는 것
 *
 * self FK 에 CASCADE 를 붙이지 않으므로, 원본만 지워지고 역분개 행이 고아로 남는 상태는
 * 만들어지지 않는다. 존재하지 않는 원본을 가리키는 취소 행은 감사 추적을 망가뜨린다.
 */
ALTER TABLE ugc_shorts_pilot_events
    DROP CONSTRAINT IF EXISTS fk_shorts_pilot_events_reverses;

ALTER TABLE ugc_shorts_pilot_events
    ADD CONSTRAINT fk_shorts_pilot_events_reverses
        FOREIGN KEY (reverses_event_id) REFERENCES ugc_shorts_pilot_events(id);

COMMENT ON COLUMN ugc_shorts_pilot_events.reverses_event_id IS
    'OPERATOR_ENTRY_REVERSED 전용. 무효화 대상 원본 이벤트 id 다. 원본은 지우지 않고 '
    '그대로 남으며, 보고서가 합계에서만 제외한다.';

-- 기존 CHECK 재정의로 새 이벤트 타입을 추가한다(V98/V99 와 같은 방식).
-- 기존 여섯 타입은 그대로 유지되므로 기존 행은 전부 유효하다.
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
            'OPERATOR_EXTERNAL_COST_LOGGED',
            'OPERATOR_ENTRY_REVERSED'
        )
    );

/*
 * 참조는 취소 이벤트에만 붙는다.
 *
 * 다른 이벤트에 값이 들어가면 "무엇이 무엇을 취소했는지"가 흐려지고, 보고서가 엉뚱한
 * 원본을 합계에서 뺀다. 기존 행은 reverses_event_id 가 NULL 이라 그대로 통과한다.
 */
ALTER TABLE ugc_shorts_pilot_events
    DROP CONSTRAINT IF EXISTS ck_shorts_pilot_events_reverses;

ALTER TABLE ugc_shorts_pilot_events
    ADD CONSTRAINT ck_shorts_pilot_events_reverses CHECK (
        (event_type = 'OPERATOR_ENTRY_REVERSED' AND reverses_event_id IS NOT NULL)
        OR (event_type <> 'OPERATOR_ENTRY_REVERSED' AND reverses_event_id IS NULL)
    );

/*
 * 취소 이벤트는 금액·시간을 갖지 않는다.
 *
 * 취소는 "이 기록을 빼라"는 지시일 뿐 새 금액이 아니다. 값이 붙으면 합계에 두 번
 * 반영될 여지가 생긴다.
 *
 * V98 의 operator_minutes CHECK 와 V99 의 amount_krw CHECK 는 손대지 않는다 —
 * OPERATOR_ENTRY_REVERSED 는 두 CHECK 모두 `<>` / `NOT IN` 분기에 걸리고 값이 NULL 이라
 * 이미 통과한다. 여기서 다시 선언하면 같은 규칙이 세 곳에 흩어진다.
 */

/*
 * 원본 하나당 취소는 하나뿐이다.
 *
 * 부분 인덱스인 이유: 취소가 아닌 행은 reverses_event_id 가 전부 NULL 인데, 일반 유니크
 * 인덱스는 NULL 을 서로 다른 값으로 보므로 굳이 포함할 이유가 없다. 조건을 붙여 취소
 * 행만 대상으로 둔다.
 *
 * 이 인덱스가 곧 동시 요청 방어선이다. 애플리케이션이 조회 후 삽입하면 요청 둘이 모두
 * "아직 취소 안 됨"을 보고 통과해 같은 원본에 취소 행이 두 개 생긴다. 그러면 보고서가
 * 같은 금액을 두 번 빼지는 않지만, 감사 추적에 설명할 수 없는 중복이 남는다.
 * INSERT ... ON CONFLICT 의 판정자로 쓰려면 이 조건식을 질의에도 그대로 적어야 한다.
 */
CREATE UNIQUE INDEX IF NOT EXISTS uq_shorts_pilot_events_reversal
    ON ugc_shorts_pilot_events (reverses_event_id)
    WHERE event_type = 'OPERATOR_ENTRY_REVERSED';
