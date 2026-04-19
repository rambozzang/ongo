-- V42__content_sources_and_drive_import.sql

CREATE TYPE content_source_type AS ENUM ('GOOGLE_DRIVE');
CREATE TYPE content_source_status AS ENUM ('ACTIVE', 'EXPIRED', 'REVOKED');
CREATE TYPE video_source AS ENUM ('UPLOAD_PC', 'GOOGLE_DRIVE');
CREATE TYPE drive_import_status AS ENUM ('PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS user_content_sources (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type             content_source_type NOT NULL,
    external_account_id     VARCHAR(255) NOT NULL,
    account_email           VARCHAR(255) NOT NULL,
    account_display_name    VARCHAR(255),
    access_token            TEXT NOT NULL,
    refresh_token           TEXT,
    token_expires_at        TIMESTAMP,
    granted_scopes          TEXT,
    status                  content_source_status NOT NULL DEFAULT 'ACTIVE',
    last_error              TEXT,
    connected_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at            TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_content_sources UNIQUE (user_id, source_type)
);
CREATE INDEX idx_user_content_sources_user ON user_content_sources(user_id);

COMMENT ON TABLE user_content_sources IS '영상 입력 소스 연결 (드라이브 등)';
COMMENT ON COLUMN user_content_sources.access_token IS 'AES-256 암호화된 OAuth 액세스 토큰';
COMMENT ON COLUMN user_content_sources.refresh_token IS 'AES-256 암호화된 OAuth 리프레시 토큰';
COMMENT ON COLUMN user_content_sources.external_account_id IS 'OAuth sub claim — 이메일 변경돼도 안정';

ALTER TABLE videos
    ADD COLUMN source              video_source NOT NULL DEFAULT 'UPLOAD_PC',
    ADD COLUMN source_reference    JSONB;

COMMENT ON COLUMN videos.source IS '영상 원본 출처 (PC 업로드 / 구글 드라이브)';
COMMENT ON COLUMN videos.source_reference IS '소스별 원본 참조 JSON';

CREATE INDEX idx_videos_user_source ON videos(user_id, source);

CREATE TABLE IF NOT EXISTS drive_import_jobs (
    id                      BIGSERIAL PRIMARY KEY,
    video_id                BIGINT NOT NULL UNIQUE REFERENCES videos(id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_source_id       BIGINT NOT NULL REFERENCES user_content_sources(id) ON DELETE RESTRICT,
    drive_file_id           VARCHAR(255) NOT NULL,
    drive_file_name         VARCHAR(500) NOT NULL,
    file_size_bytes         BIGINT NOT NULL,
    bytes_transferred       BIGINT NOT NULL DEFAULT 0,
    status                  drive_import_status NOT NULL DEFAULT 'PENDING',
    s3_key                  TEXT,
    error_message           TEXT,
    retry_count             INTEGER NOT NULL DEFAULT 0,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_drive_import_bytes CHECK (bytes_transferred >= 0 AND bytes_transferred <= file_size_bytes)
);
CREATE INDEX idx_drive_import_jobs_user_status ON drive_import_jobs(user_id, status);
CREATE INDEX idx_drive_import_jobs_status_updated ON drive_import_jobs(status, updated_at)
    WHERE status IN ('PENDING', 'DOWNLOADING');

COMMENT ON TABLE drive_import_jobs IS '구글 드라이브 → S3 복사 작업 추적';
