-- V26: 콘텐츠 제작 관련 테이블
-- scripts, repurpose, rewrite, versioning, series, subtitles, resize, music

-- 1. AI 스크립트 작성기
CREATE TABLE IF NOT EXISTS scripts (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    title           VARCHAR(200)    NOT NULL,
    topic           VARCHAR(300)    NOT NULL,
    format          VARCHAR(30)     NOT NULL DEFAULT 'LONG_FORM',
    tone            VARCHAR(30)     NOT NULL DEFAULT 'CASUAL',
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    target_duration INT             NOT NULL DEFAULT 600,
    estimated_word_count INT        NOT NULL DEFAULT 0,
    keywords        TEXT[]          DEFAULT '{}',
    target_audience VARCHAR(200),
    notes           TEXT,
    credit_cost     INT             NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_scripts_user_id ON scripts(user_id);
CREATE INDEX IF NOT EXISTS idx_scripts_status ON scripts(status);

CREATE TABLE IF NOT EXISTS script_sections (
    id              BIGSERIAL       PRIMARY KEY,
    script_id       BIGINT          NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    type            VARCHAR(20)     NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    content         TEXT            NOT NULL DEFAULT '',
    duration        INT             NOT NULL DEFAULT 0,
    notes           TEXT,
    order_number    INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_script_sections_script_id ON script_sections(script_id);

CREATE TABLE IF NOT EXISTS script_templates (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES users(id),
    name            VARCHAR(200)    NOT NULL,
    description     TEXT,
    format          VARCHAR(30)     NOT NULL,
    tone            VARCHAR(30)     NOT NULL,
    sections        JSONB           NOT NULL DEFAULT '[]',
    usage_count     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 2. 콘텐츠 리퍼포징
CREATE TABLE IF NOT EXISTS repurpose_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    original_title      VARCHAR(500) NOT NULL,
    original_platform   VARCHAR(50) NOT NULL,
    target_platform     VARCHAR(50) NOT NULL,
    target_format       VARCHAR(50) NOT NULL,
    status              VARCHAR(30) DEFAULT 'PENDING',
    progress            INT DEFAULT 0,
    output_url          VARCHAR(1000),
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_repurpose_jobs_workspace ON repurpose_jobs(workspace_id);
CREATE INDEX IF NOT EXISTS idx_repurpose_jobs_status ON repurpose_jobs(status);

CREATE TABLE IF NOT EXISTS repurpose_templates (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT,
    name                VARCHAR(200) NOT NULL,
    source_platform     VARCHAR(50) NOT NULL,
    target_platform     VARCHAR(50) NOT NULL,
    target_format       VARCHAR(50) NOT NULL,
    description         TEXT,
    is_default          BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_repurpose_templates_workspace ON repurpose_templates(workspace_id);

-- 3. 콘텐츠 리라이터
CREATE TABLE IF NOT EXISTS content_rewrite_results (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    source_text TEXT NOT NULL,
    source_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    target_formats VARCHAR(500) DEFAULT '[]',
    results JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_content_rewrite_results_user_id ON content_rewrite_results(user_id);

-- 4. 콘텐츠 버전 관리
CREATE TABLE IF NOT EXISTS content_version_groups (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    content_id      BIGINT NOT NULL,
    content_title   VARCHAR(300) NOT NULL,
    platform        VARCHAR(30) NOT NULL,
    total_versions  INT NOT NULL DEFAULT 0,
    latest_version  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_content_version_groups_workspace ON content_version_groups(workspace_id);

CREATE TABLE IF NOT EXISTS content_versions (
    id                  BIGSERIAL PRIMARY KEY,
    group_id            BIGINT NOT NULL REFERENCES content_version_groups(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    change_type         VARCHAR(20) NOT NULL,
    change_description  TEXT NOT NULL,
    previous_value      TEXT,
    new_value           TEXT,
    perf_before_views   BIGINT,
    perf_before_likes   INT,
    perf_before_engagement NUMERIC(5,2),
    perf_before_ctr     NUMERIC(5,2),
    perf_after_views    BIGINT,
    perf_after_likes    INT,
    perf_after_engagement NUMERIC(5,2),
    perf_after_ctr      NUMERIC(5,2),
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_content_versions_group ON content_versions(group_id);

-- 5. 콘텐츠 시리즈
CREATE TABLE IF NOT EXISTS content_series (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    description TEXT,
    cover_image_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    platform VARCHAR(30) NOT NULL DEFAULT 'YOUTUBE',
    frequency VARCHAR(30) NOT NULL DEFAULT 'WEEKLY',
    custom_frequency_days INT,
    tags VARCHAR(500) DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_content_series_user_id ON content_series(user_id);
CREATE INDEX IF NOT EXISTS idx_content_series_status ON content_series(status);

CREATE TABLE IF NOT EXISTS series_episodes (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES content_series(id) ON DELETE CASCADE,
    episode_number INT NOT NULL DEFAULT 1,
    title VARCHAR(300) NOT NULL,
    video_id VARCHAR(100),
    platform VARCHAR(30) NOT NULL DEFAULT 'YOUTUBE',
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    scheduled_date TIMESTAMPTZ,
    published_date TIMESTAMPTZ,
    views BIGINT DEFAULT 0,
    likes BIGINT DEFAULT 0,
    comments BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_series_episodes_series_id ON series_episodes(series_id);

-- 6. 자막 편집기
CREATE TABLE IF NOT EXISTS subtitle_tracks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL,
    video_title VARCHAR(500),
    language VARCHAR(10) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    cues JSONB DEFAULT '[]',
    total_duration NUMERIC(10,2) DEFAULT 0,
    word_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_subtitle_tracks_user ON subtitle_tracks(user_id);
CREATE INDEX IF NOT EXISTS idx_subtitle_tracks_video ON subtitle_tracks(video_id);

-- 7. AI 자막 생성기
CREATE TABLE IF NOT EXISTS subtitle_jobs (
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
CREATE INDEX IF NOT EXISTS idx_subtitle_jobs_user ON subtitle_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_subtitle_jobs_status ON subtitle_jobs(status);

CREATE TABLE IF NOT EXISTS subtitle_segments (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT NOT NULL REFERENCES subtitle_jobs(id) ON DELETE CASCADE,
    start_time      NUMERIC(10,3) NOT NULL,
    end_time        NUMERIC(10,3) NOT NULL,
    text            TEXT NOT NULL,
    confidence      NUMERIC(5,4) NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_subtitle_segments_job ON subtitle_segments(job_id);

-- 8. 자막 번역
CREATE TABLE IF NOT EXISTS subtitle_translations (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    video_title         VARCHAR(300) NOT NULL,
    source_language     VARCHAR(10) NOT NULL,
    target_language     VARCHAR(10) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress            INT NOT NULL DEFAULT 0,
    source_line_count   INT NOT NULL DEFAULT 0,
    translated_line_count INT NOT NULL DEFAULT 0,
    quality             INT NOT NULL DEFAULT 0,
    cost                INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_subtitle_translations_workspace ON subtitle_translations(workspace_id);

CREATE TABLE IF NOT EXISTS translation_lines (
    id              BIGSERIAL PRIMARY KEY,
    translation_id  BIGINT NOT NULL REFERENCES subtitle_translations(id) ON DELETE CASCADE,
    line_number     INT NOT NULL,
    start_time      VARCHAR(20) NOT NULL,
    end_time        VARCHAR(20) NOT NULL,
    source_text     TEXT NOT NULL,
    translated_text TEXT NOT NULL DEFAULT '',
    is_edited       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_translation_lines_translation ON translation_lines(translation_id);

CREATE TABLE IF NOT EXISTS supported_languages (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(10) NOT NULL UNIQUE,
    name            VARCHAR(50) NOT NULL,
    native_name     VARCHAR(50) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 9. 영상 리사이저
CREATE TABLE IF NOT EXISTS resize_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL,
    video_title VARCHAR(500),
    original_aspect_ratio VARCHAR(10),
    target_aspect_ratio VARCHAR(10) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    output_url TEXT,
    thumbnail_url TEXT,
    smart_crop BOOLEAN DEFAULT true,
    focus_point_x NUMERIC(5,2),
    focus_point_y NUMERIC(5,2),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_resize_jobs_user ON resize_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_resize_jobs_video ON resize_jobs(video_id);

-- 10. AI 음악 추천기
CREATE TABLE IF NOT EXISTS music_tracks (
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
CREATE INDEX IF NOT EXISTS idx_music_tracks_genre ON music_tracks(genre);
CREATE INDEX IF NOT EXISTS idx_music_tracks_mood ON music_tracks(mood);

CREATE TABLE IF NOT EXISTS music_recommendations (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    video_id            BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    selected_track_id   BIGINT REFERENCES music_tracks(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_music_recs_user ON music_recommendations(user_id);

CREATE TABLE IF NOT EXISTS recommendation_tracks (
    recommendation_id   BIGINT NOT NULL REFERENCES music_recommendations(id) ON DELETE CASCADE,
    track_id            BIGINT NOT NULL REFERENCES music_tracks(id),
    match_score         INT NOT NULL DEFAULT 0,
    PRIMARY KEY (recommendation_id, track_id)
);
