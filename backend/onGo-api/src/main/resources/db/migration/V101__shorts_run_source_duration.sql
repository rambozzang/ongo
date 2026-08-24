-- 쇼츠 실행에 인용된 원본 길이를 고정한다.
--
-- ## 왜 필요한가
--
-- TRANSCRIBE 는 원본을 조각내 조각마다 전사 모델을 부르므로 **원가가 길이에 정비례**한다.
-- 그런데 과금은 AiFeature.STT 정액이었다. 길이 상한이 3시간이라 같은 10 크레딧으로
-- 1 분짜리와 180 분짜리를 처리했고, 긴 원본일수록 손실이 커졌다.
--
-- 이 컬럼이 생기면 차감액을 길이에서 계산할 수 있다.
--
-- ## 왜 videos 가 아니라 runs 인가
--
-- 청구 근거는 **그 실행에 인용된 값**이어야 한다. 영상 행은 재업로드·임포트로 갱신될 수
-- 있고 여러 실행이 한 영상을 공유한다. 근거가 영상 쪽에 있으면 오래된 실행을 재실행할 때
-- 인용된 적 없는 금액이 청구된다. 실행에 고정해야 재실행이 첫 견적과 같은 금액을 낸다.
--
-- ## 기존 행
--
-- NULL 로 남는다. 소급 추정하지도, 다시 프로브하지도 않는다. NULL 은 "이 변경 이전에
-- 만들어진 실행"이라는 정직한 표시이며, 애플리케이션은 그 경우 종전대로 정액을 매긴다.
-- 백필하면 측정한 적 없는 값으로 과거 실행의 청구 근거를 지어내는 셈이 된다.

ALTER TABLE ugc_shorts_pipeline_runs
    ADD COLUMN IF NOT EXISTS source_duration_ms BIGINT;

-- 0 이나 음수는 길이가 아니다. 애플리케이션이 이미 걸러내지만, 그 검사를 우회한 경로가
-- 생기면 조용히 0 원 청구가 되므로 스키마에서도 막는다.
ALTER TABLE ugc_shorts_pipeline_runs
    DROP CONSTRAINT IF EXISTS ck_shorts_runs_source_duration;

ALTER TABLE ugc_shorts_pipeline_runs
    ADD CONSTRAINT ck_shorts_runs_source_duration
        CHECK (source_duration_ms IS NULL OR source_duration_ms > 0);

COMMENT ON COLUMN ugc_shorts_pipeline_runs.source_duration_ms IS
    '실행 생성 시 서버가 ffprobe 로 측정해 수락한 원본 길이(ms). 전사 크레딧 산정의 근거이며 '
    '재실행도 같은 값을 쓴다. 생성 후 변경하지 않는다. NULL 은 이 컬럼 도입 이전 실행이며 '
    '전사 크레딧을 정액으로 매긴다 — 소급 측정하지 않는다.';
