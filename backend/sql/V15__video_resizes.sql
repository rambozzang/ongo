-- V15__video_resizes.sql
-- 영상 리사이징 결과 저장

CREATE TABLE video_resizes (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    original_video_id BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    aspect_ratio      VARCHAR(10) NOT NULL,
    width             INT NOT NULL,
    height            INT NOT NULL,
    file_url          TEXT,
    file_size_bytes   BIGINT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message     TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP
);

CREATE INDEX idx_video_resizes_user ON video_resizes(user_id);
CREATE INDEX idx_video_resizes_original ON video_resizes(original_video_id);
