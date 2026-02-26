-- AI 음악 추천기
CREATE TABLE music_tracks (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(300) NOT NULL,
    artist          VARCHAR(200) NOT NULL,
    genre           VARCHAR(100) NOT NULL,
    mood            VARCHAR(100) NOT NULL,
    bpm             INT NOT NULL DEFAULT 0,
    duration        INT NOT NULL DEFAULT 0,
    preview_url     VARCHAR(1000),
    license_type    VARCHAR(20) NOT NULL DEFAULT 'FREE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE music_recommendations (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    video_id            BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    selected_track_id   BIGINT REFERENCES music_tracks(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recommendation_tracks (
    recommendation_id   BIGINT NOT NULL REFERENCES music_recommendations(id) ON DELETE CASCADE,
    track_id            BIGINT NOT NULL REFERENCES music_tracks(id),
    match_score         INT NOT NULL DEFAULT 0,
    PRIMARY KEY (recommendation_id, track_id)
);

CREATE INDEX idx_music_recs_user ON music_recommendations(user_id);
CREATE INDEX idx_music_recs_status ON music_recommendations(status);
CREATE INDEX idx_music_tracks_genre ON music_tracks(genre);
CREATE INDEX idx_music_tracks_mood ON music_tracks(mood);
