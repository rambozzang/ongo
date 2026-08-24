-- 유료 쇼츠 파일럿 측정: 사람 투입 시간.
--
-- 리드타임과 재실행·렌더 실패는 시스템이 자동으로 남기지만, "이 건에 사람이 몇 분을
-- 썼나"는 어디에도 신호가 없다. 자동 추정할 방법이 없으므로 운영자가 직접 입력한다.
-- 추정치를 넣으면 그 순간 원가 판단의 근거가 사라진다.

ALTER TABLE ugc_shorts_pilot_events
    ADD COLUMN IF NOT EXISTS operator_minutes INT;

COMMENT ON COLUMN ugc_shorts_pilot_events.operator_minutes IS
    'OPERATOR_TIME_LOGGED 전용. 운영자가 직접 입력한 분 단위 투입 시간이며 추정치가 아니다.';

-- 기존 CHECK 를 재정의해 새 이벤트 타입을 추가한다.
-- V97 이 만든 세 타입은 그대로 유지되므로 기존 행은 전부 유효하다.
ALTER TABLE ugc_shorts_pilot_events
    DROP CONSTRAINT IF EXISTS ck_shorts_pilot_events_type;

ALTER TABLE ugc_shorts_pilot_events
    ADD CONSTRAINT ck_shorts_pilot_events_type CHECK (
        event_type IN (
            'PILOT_ENROLLED',
            'STAGE_RERUN',
            'RENDER_ATTEMPT_FAILED',
            'OPERATOR_TIME_LOGGED'
        )
    );

/*
 * 분 단위 값은 이 이벤트에만 붙는다.
 *
 * 상한 1440(24시간)은 오타 방어다. 한 건에 하루를 넘겨 쓴 경우라면 두 번 나눠 기록하는
 * 편이 정확하고, 그 편이 "언제 얼마나" 썼는지도 남는다. 하한 1은 0분 기록을 막는다 —
 * 0 은 "안 썼다"와 "기록을 깜빡했다"를 구분하지 못해 집계를 오염시킨다.
 *
 * 다른 이벤트에 값이 붙으면 합계가 조용히 부풀어 오르므로 NULL 만 허용한다.
 * 기존 행은 operator_minutes 가 NULL 이라 이 조건을 그대로 만족한다.
 */
ALTER TABLE ugc_shorts_pilot_events
    DROP CONSTRAINT IF EXISTS ck_shorts_pilot_events_operator_minutes;

ALTER TABLE ugc_shorts_pilot_events
    ADD CONSTRAINT ck_shorts_pilot_events_operator_minutes CHECK (
        (event_type = 'OPERATOR_TIME_LOGGED' AND operator_minutes BETWEEN 1 AND 1440)
        OR (event_type <> 'OPERATOR_TIME_LOGGED' AND operator_minutes IS NULL)
    );
