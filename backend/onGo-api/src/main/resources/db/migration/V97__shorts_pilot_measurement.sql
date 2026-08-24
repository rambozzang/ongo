-- 유료 쇼츠 파일럿(5~10명) 측정 기반.
--
-- 목적은 분석 플랫폼이 아니라 **증거 소실 방지**다. 지금은 다음 신호가 매일 사라진다:
--   1) rerunStage 가 ugc_shorts_run_stages 행을 하드 삭제해 재실행 흔적이 남지 않는다.
--   2) shorts_render_jobs 는 (run_id, clip_id) 유니크라 재시도가 같은 행을 덮어써
--      실패 이력이 사라진다.
--   3) 실행 시작·첫 납품 시각이 없어 리드타임을 계산할 수 없다(updated_at 은 이후
--      어떤 수정에도 갱신되므로 완료 시점 대용이 될 수 없다).
--
-- 그래서 append-only 이벤트 테이블 하나와 타임스탬프 두 개만 추가한다.

-- 실행 시작/첫 납품 시각. 둘 다 nullable 이며 기존 행은 NULL 로 남는다 —
-- 소급 추정하지 않는다. 값이 없다는 것이 "측정 시작 전"이라는 정직한 표시다.
ALTER TABLE ugc_shorts_pipeline_runs
    ADD COLUMN IF NOT EXISTS started_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;

COMMENT ON COLUMN ugc_shorts_pipeline_runs.started_at IS
    '파이프라인이 처음 실행된 시각. 재실행/재개가 덮어쓰지 않는다.';
COMMENT ON COLUMN ugc_shorts_pipeline_runs.delivered_at IS
    '첫 클립 렌더가 완료된 시각. 리드타임의 종점이며 성공의 증거다.';

-- --------------------------------------------------------------------------
-- ugc_shorts_pilot_events: append-only 측정 이벤트
--
-- 콘텐츠를 담지 않는다. 자막·후킹 문구·원본 URL·자유 텍스트가 들어오면 저작물과
-- 발화 내용이 측정 테이블로 새고, 계정 삭제 시 지울 대상도 늘어난다.
--
-- clip_id 를 두지 않는 이유: rerunStage 는 클립을 통째로 삭제한다. 클립 FK 가 있으면
-- ON DELETE CASCADE 로 이벤트까지 함께 사라져 "재실행이 있었다"는 증거가
-- 정확히 그 순간에 없어진다. 측정 대상은 run 이고 clip 이 아니다.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ugc_shorts_pilot_events (
    id         BIGSERIAL PRIMARY KEY,
    run_id     BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    actor_type VARCHAR(10) NOT NULL,
    -- 내부 users.id 만 담는다. 이메일·이름 같은 식별 정보는 저장하지 않는다.
    -- SYSTEM 이벤트는 NULL 이다.
    actor_id   BIGINT,
    -- RENDER_ATTEMPT_FAILED 의 시도 회차. 같은 클립을 여러 번 시도해도 회차가 달라
    -- 행이 누적되고, 덮어쓰기가 아니라는 것을 이 값으로 확인한다.
    attempt_no INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_shorts_pilot_events_type CHECK (
        event_type IN ('PILOT_ENROLLED', 'STAGE_RERUN', 'RENDER_ATTEMPT_FAILED')
    ),
    CONSTRAINT ck_shorts_pilot_events_actor CHECK (
        actor_type IN ('ADMIN', 'CUSTOMER', 'SYSTEM')
    ),
    CONSTRAINT ck_shorts_pilot_events_attempt CHECK (attempt_no IS NULL OR attempt_no >= 0)
);

CREATE INDEX IF NOT EXISTS idx_shorts_pilot_events_run
    ON ugc_shorts_pilot_events(run_id, created_at);

-- 코호트는 run 당 한 번만 정의된다. 중복 등록을 애플리케이션 검사에만 맡기면
-- 동시 요청 둘이 모두 통과해 파일럿 인원이 실제보다 많아 보인다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_shorts_pilot_events_enrollment
    ON ugc_shorts_pilot_events(run_id)
    WHERE event_type = 'PILOT_ENROLLED';

COMMENT ON TABLE ugc_shorts_pilot_events IS
    '쇼츠 파일럿 측정 이벤트(append-only). 수정·삭제 경로를 만들지 않는다.';
