-- AI 콘텐츠 일정 최적화
CREATE TABLE optimal_slots (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    platform            VARCHAR(50) NOT NULL,
    day_of_week         VARCHAR(20) NOT NULL,
    hour                INT NOT NULL,
    score               INT NOT NULL DEFAULT 0,
    audience_online     INT NOT NULL DEFAULT 0,
    competition_level   VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    reason              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE schedule_recommendations (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id),
    video_id                BIGINT NOT NULL,
    video_title             VARCHAR(300) NOT NULL,
    current_schedule        TIMESTAMPTZ,
    recommended_schedule    TIMESTAMPTZ NOT NULL,
    platform                VARCHAR(50) NOT NULL,
    expected_improvement    INT NOT NULL DEFAULT 0,
    confidence              INT NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_optimal_slots_user ON optimal_slots(user_id);
CREATE INDEX idx_optimal_slots_platform ON optimal_slots(platform);
CREATE INDEX idx_schedule_recs_user ON schedule_recommendations(user_id);
CREATE INDEX idx_schedule_recs_status ON schedule_recommendations(status);
