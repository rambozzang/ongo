-- AI 자막 생성기
CREATE TABLE subtitle_jobs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    video_id        BIGINT NOT NULL,
    video_title     VARCHAR(500) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    language        VARCHAR(10) NOT NULL DEFAULT 'ko',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress        INT NOT NULL DEFAULT 0,
    subtitle_url    VARCHAR(1000),
    word_count      INT NOT NULL DEFAULT 0,
    duration        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE TABLE subtitle_segments (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT NOT NULL REFERENCES subtitle_jobs(id) ON DELETE CASCADE,
    start_time      NUMERIC(10,3) NOT NULL,
    end_time        NUMERIC(10,3) NOT NULL,
    text            TEXT NOT NULL,
    confidence      NUMERIC(5,4) NOT NULL DEFAULT 0
);

CREATE INDEX idx_subtitle_jobs_user ON subtitle_jobs(user_id);
CREATE INDEX idx_subtitle_jobs_status ON subtitle_jobs(status);
CREATE INDEX idx_subtitle_segments_job ON subtitle_segments(job_id);
