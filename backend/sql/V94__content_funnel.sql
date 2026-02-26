-- 콘텐츠 퍼널 분석
CREATE TABLE funnel_stages (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    video_id        BIGINT NOT NULL,
    video_title     VARCHAR(300) NOT NULL,
    stage           VARCHAR(30) NOT NULL,
    count           BIGINT NOT NULL DEFAULT 0,
    rate            NUMERIC(8,4) NOT NULL DEFAULT 0,
    drop_off        NUMERIC(8,4) NOT NULL DEFAULT 0,
    measured_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE funnel_comparisons (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    video_id_a      BIGINT NOT NULL,
    video_title_a   VARCHAR(300) NOT NULL,
    video_id_b      BIGINT NOT NULL,
    video_title_b   VARCHAR(300) NOT NULL,
    winner          VARCHAR(5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_funnel_stages_user ON funnel_stages(user_id);
CREATE INDEX idx_funnel_stages_video ON funnel_stages(video_id);
CREATE INDEX idx_funnel_comparisons_user ON funnel_comparisons(user_id);
