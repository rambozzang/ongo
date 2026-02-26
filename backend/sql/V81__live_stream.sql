-- 라이브 스트림 관리
CREATE TABLE live_streams (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    platform        VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at    TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    viewer_count    INT NOT NULL DEFAULT 0,
    peak_viewers    INT NOT NULL DEFAULT 0,
    chat_messages   INT NOT NULL DEFAULT 0,
    stream_url      VARCHAR(1000),
    thumbnail_url   VARCHAR(1000)
);

CREATE TABLE stream_chats (
    id              BIGSERIAL PRIMARY KEY,
    stream_id       BIGINT NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    username        VARCHAR(200) NOT NULL,
    message         TEXT NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_highlighted  BOOLEAN NOT NULL DEFAULT false,
    is_moderator    BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_live_streams_user ON live_streams(user_id);
CREATE INDEX idx_live_streams_status ON live_streams(status);
CREATE INDEX idx_live_streams_scheduled ON live_streams(scheduled_at);
CREATE INDEX idx_stream_chats_stream ON stream_chats(stream_id);
