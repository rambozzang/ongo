-- 재생목록 관리
CREATE TABLE playlists (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    title               VARCHAR(200)    NOT NULL,
    description         TEXT,
    platform            VARCHAR(30)     NOT NULL,
    platform_playlist_id VARCHAR(200),
    visibility          VARCHAR(20)     NOT NULL DEFAULT 'PUBLIC',
    thumbnail_url       VARCHAR(500),
    video_count         INT             NOT NULL DEFAULT 0,
    total_views         BIGINT          NOT NULL DEFAULT 0,
    total_duration      INT             NOT NULL DEFAULT 0,
    tags                TEXT[]          DEFAULT '{}',
    is_synced           BOOLEAN         NOT NULL DEFAULT false,
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE playlist_videos (
    id              BIGSERIAL       PRIMARY KEY,
    playlist_id     BIGINT          NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    video_id        VARCHAR(100)    NOT NULL,
    title           VARCHAR(300)    NOT NULL,
    thumbnail_url   VARCHAR(500),
    duration        INT             NOT NULL DEFAULT 0,
    views           BIGINT          NOT NULL DEFAULT 0,
    position        INT             NOT NULL DEFAULT 0,
    added_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_playlists_user_id ON playlists(user_id);
CREATE INDEX idx_playlists_platform ON playlists(platform);
CREATE INDEX idx_playlist_videos_playlist ON playlist_videos(playlist_id);
CREATE INDEX idx_playlist_videos_position ON playlist_videos(playlist_id, position);
