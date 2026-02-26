-- 콘텐츠 A/B 분석기
CREATE TABLE content_ab_tests (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    winner          VARCHAR(5),
    confidence      NUMERIC(5,2) NOT NULL DEFAULT 0,
    start_date      DATE NOT NULL,
    end_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE content_variants (
    id              BIGSERIAL PRIMARY KEY,
    test_id         BIGINT NOT NULL REFERENCES content_ab_tests(id) ON DELETE CASCADE,
    label           VARCHAR(200) NOT NULL,
    video_id        BIGINT NOT NULL,
    video_title     VARCHAR(500) NOT NULL,
    views           BIGINT NOT NULL DEFAULT 0,
    likes           BIGINT NOT NULL DEFAULT 0,
    comments        BIGINT NOT NULL DEFAULT 0,
    ctr             NUMERIC(5,2) NOT NULL DEFAULT 0,
    avg_watch_time  INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_content_ab_user ON content_ab_tests(user_id);
CREATE INDEX idx_content_ab_status ON content_ab_tests(status);
CREATE INDEX idx_content_variants_test ON content_variants(test_id);
