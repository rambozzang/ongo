-- REFRAME 단계가 산출한 크롭 좌표를 실행(run)에 보존한다.
--
-- 기존에는 오케스트레이터의 메모리 컨텍스트에만 있었다. 파이프라인은 HOOK 이후
-- AWAITING_HOOK_SELECTION 에서 멈췄다가 사용자가 후킹을 고르면 TEMPLATE 부터 재개하는데,
-- 재개 시 컨텍스트를 DB 에서 다시 만들면서 크롭이 복원되지 않아 소실됐다.
-- TEMPLATE 은 크롭이 없으면 조기 반환하므로 클립에 크롭이 기록되지 않고,
-- 결과적으로 render-spec 의 crop 이 비어 세로 변환 없이 렌더되는 결함이 있었다.
--
-- transcript_text 는 이미 같은 이유로 실행에 보존되고 있다. 크롭도 동일하게 맞춘다.
ALTER TABLE ugc_shorts_pipeline_runs ADD COLUMN IF NOT EXISTS crop_json TEXT;

COMMENT ON COLUMN ugc_shorts_pipeline_runs.crop_json IS
    'REFRAME 산출 크롭 좌표 JSON. 게이트 재개 시 TEMPLATE 단계가 클립에 복사한다';
