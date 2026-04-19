-- V40: AI 콘텐츠 리퍼포징 클립 테이블
-- repurpose_jobs에 user_id/video_id/clip_count 컬럼 추가 및 repurpose_clips 신규 생성

-- repurpose_jobs 테이블에 AI 리퍼포징용 컬럼 추가
ALTER TABLE repurpose_jobs
    ADD COLUMN IF NOT EXISTS user_id  BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS video_id BIGINT REFERENCES videos(id),
    ADD COLUMN IF NOT EXISTS video_title VARCHAR(500),
    ADD COLUMN IF NOT EXISTS clip_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_repurpose_jobs_user_id ON repurpose_jobs(user_id);

-- repurpose_clips: AI가 추천한 숏폼 클립 후보
CREATE TABLE IF NOT EXISTS repurpose_clips (
    id                  BIGSERIAL PRIMARY KEY,
    job_id              BIGINT NOT NULL REFERENCES repurpose_jobs(id) ON DELETE CASCADE,
    start_time          VARCHAR(20)  NOT NULL,
    end_time            VARCHAR(20)  NOT NULL,
    title               VARCHAR(500) NOT NULL,
    description         TEXT,
    viral_score         INT          NOT NULL DEFAULT 0,
    reasoning           TEXT,
    suggested_platform  VARCHAR(50)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_repurpose_clips_job_id ON repurpose_clips(job_id);
