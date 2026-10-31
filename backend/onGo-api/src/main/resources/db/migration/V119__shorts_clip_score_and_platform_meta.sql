-- 쇼츠 클립 선택 품질(S1)과 플랫폼별 메타(S3).
--
-- 실행: 사용자가 정한 클립 개수·길이. NULL 은 "지정 안 함"이며 서버 기본값을 쓴다.
--   이 컬럼 도입 이전 실행도 NULL 이라 종전처럼 동작한다.
-- 클립: AI 가 매긴 점수(0~100)와 그 이유, 플랫폼별 제목·설명·해시태그.
--   점수가 NULL 이면 AI 가 점수를 주지 않은 것이다. 지어내지 않는다.

ALTER TABLE ugc_shorts_pipeline_runs
    ADD COLUMN IF NOT EXISTS segment_clip_count INT,
    ADD COLUMN IF NOT EXISTS segment_min_duration_ms BIGINT,
    ADD COLUMN IF NOT EXISTS segment_max_duration_ms BIGINT;

ALTER TABLE ugc_shorts_pipeline_runs
    ADD CONSTRAINT chk_ugc_shorts_runs_segment_clip_count
        CHECK (segment_clip_count IS NULL OR segment_clip_count BETWEEN 1 AND 10),
    ADD CONSTRAINT chk_ugc_shorts_runs_segment_duration
        CHECK (
            (segment_min_duration_ms IS NULL OR segment_min_duration_ms BETWEEN 5000 AND 180000)
            AND (segment_max_duration_ms IS NULL OR segment_max_duration_ms BETWEEN 5000 AND 180000)
            AND (segment_min_duration_ms IS NULL OR segment_max_duration_ms IS NULL
                 OR segment_min_duration_ms <= segment_max_duration_ms)
        );

ALTER TABLE ugc_shorts_clips
    ADD COLUMN IF NOT EXISTS score SMALLINT,
    ADD COLUMN IF NOT EXISTS score_reason TEXT,
    ADD COLUMN IF NOT EXISTS platform_meta JSONB;

ALTER TABLE ugc_shorts_clips
    ADD CONSTRAINT chk_ugc_shorts_clips_score
        CHECK (score IS NULL OR score BETWEEN 0 AND 100);
