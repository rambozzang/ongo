-- V33: 기타 테이블
-- brand_safety, mood_board, multi_brand, team_perf, playlist, academy, portfolio_builder, copyright, translator, social_listening

-- 1. 브랜드 안전성
CREATE TABLE IF NOT EXISTS brand_safety_checks (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    content_title   VARCHAR(300) NOT NULL,
    content_type    VARCHAR(20) NOT NULL,
    platform        VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    overall_score   INT NOT NULL DEFAULT 0,
    checked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_brand_safety_checks_workspace ON brand_safety_checks(workspace_id);

CREATE TABLE IF NOT EXISTS safety_check_items (
    id              BIGSERIAL PRIMARY KEY,
    check_id        BIGINT NOT NULL REFERENCES brand_safety_checks(id) ON DELETE CASCADE,
    category        VARCHAR(50) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'PASS',
    severity        VARCHAR(20) NOT NULL DEFAULT 'LOW',
    description     TEXT NOT NULL,
    recommendation  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_safety_check_items_check ON safety_check_items(check_id);

CREATE TABLE IF NOT EXISTS brand_safety_rules (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    description     TEXT NOT NULL,
    is_enabled      BOOLEAN NOT NULL DEFAULT true,
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_brand_safety_rules_workspace ON brand_safety_rules(workspace_id);

-- 2. 무드보드
CREATE TABLE IF NOT EXISTS mood_boards (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    item_count          INT DEFAULT 0,
    cover_image         VARCHAR(1000),
    tags                JSONB DEFAULT '[]',
    is_public           BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mood_boards_workspace ON mood_boards(workspace_id);

CREATE TABLE IF NOT EXISTS mood_board_items (
    id                  BIGSERIAL PRIMARY KEY,
    board_id            BIGINT NOT NULL REFERENCES mood_boards(id) ON DELETE CASCADE,
    type                VARCHAR(50) NOT NULL,
    title               VARCHAR(300),
    image_url           VARCHAR(1000),
    source_url          VARCHAR(1000),
    note                TEXT,
    color               VARCHAR(20),
    position            INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mood_board_items_board ON mood_board_items(board_id);

-- 3. 멀티 브랜드 캘린더
CREATE TABLE IF NOT EXISTS brands (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    logo_url VARCHAR(500),
    color VARCHAR(20) NOT NULL DEFAULT 'BLUE',
    category VARCHAR(100),
    assigned_editors VARCHAR(500) DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brands_user_id ON brands(user_id);

CREATE TABLE IF NOT EXISTS brand_schedule_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    brand_id BIGINT NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    assigned_to VARCHAR(200),
    video_id VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_schedule_items_user_id ON brand_schedule_items(user_id);
CREATE INDEX IF NOT EXISTS idx_brand_schedule_items_brand_id ON brand_schedule_items(brand_id);

-- 4. 팀 성과
CREATE TABLE IF NOT EXISTS team_member_performances (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL REFERENCES workspaces(id),
    name                VARCHAR(100) NOT NULL,
    email               VARCHAR(200) NOT NULL,
    role                VARCHAR(100) NOT NULL,
    avatar              TEXT,
    tasks_completed     INT NOT NULL DEFAULT 0,
    tasks_assigned      INT NOT NULL DEFAULT 0,
    completion_rate     NUMERIC(5,2) NOT NULL DEFAULT 0,
    avg_completion_time NUMERIC(5,2) NOT NULL DEFAULT 0,
    content_produced    INT NOT NULL DEFAULT 0,
    on_time_rate        NUMERIC(5,2) NOT NULL DEFAULT 0,
    rating              NUMERIC(3,2) NOT NULL DEFAULT 0,
    streak              INT NOT NULL DEFAULT 0,
    last_active_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_team_member_perf_workspace ON team_member_performances(workspace_id);

CREATE TABLE IF NOT EXISTS team_activities (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    member_id       BIGINT NOT NULL REFERENCES team_member_performances(id) ON DELETE CASCADE,
    member_name     VARCHAR(100) NOT NULL,
    action          VARCHAR(200) NOT NULL,
    target          VARCHAR(300) NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_team_activities_workspace ON team_activities(workspace_id);

-- 5. 재생목록
CREATE TABLE IF NOT EXISTS playlists (
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
CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON playlists(user_id);

CREATE TABLE IF NOT EXISTS playlist_videos (
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
CREATE INDEX IF NOT EXISTS idx_playlist_videos_playlist ON playlist_videos(playlist_id);

-- 6. 크리에이터 아카데미
CREATE TABLE IF NOT EXISTS academy_courses (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL DEFAULT 'GROWTH',
    level VARCHAR(30) NOT NULL DEFAULT 'BEGINNER',
    instructor_name VARCHAR(200) NOT NULL,
    instructor_avatar VARCHAR(500),
    thumbnail_url VARCHAR(500),
    total_lessons INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0,
    rating NUMERIC(3,2) DEFAULT 0.0,
    enrolled_count INT NOT NULL DEFAULT 0,
    credit_reward INT NOT NULL DEFAULT 0,
    tags VARCHAR(500) DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS academy_lessons (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES academy_courses(id) ON DELETE CASCADE,
    order_number INT NOT NULL DEFAULT 1,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    video_url VARCHAR(500),
    duration INT NOT NULL DEFAULT 0,
    resources JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_academy_lessons_course_id ON academy_lessons(course_id);

CREATE TABLE IF NOT EXISTS academy_enrollments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    course_id BIGINT NOT NULL REFERENCES academy_courses(id),
    completed_lessons INT NOT NULL DEFAULT 0,
    last_lesson_id BIGINT,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE(user_id, course_id)
);
CREATE INDEX IF NOT EXISTS idx_academy_enrollments_user_id ON academy_enrollments(user_id);

-- 7. 포트폴리오 빌더
CREATE TABLE IF NOT EXISTS portfolios (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    template        VARCHAR(30) NOT NULL DEFAULT 'MINIMAL',
    theme           VARCHAR(50) NOT NULL DEFAULT 'light',
    is_published    BOOLEAN NOT NULL DEFAULT false,
    public_url      VARCHAR(500),
    view_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_portfolios_user ON portfolios(user_id);

CREATE TABLE IF NOT EXISTS portfolio_sections (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    section_type    VARCHAR(30) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL DEFAULT '',
    sort_order      INT NOT NULL DEFAULT 0,
    is_visible      BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_portfolio_sections_portfolio ON portfolio_sections(portfolio_id);

-- 8. 저작권 사전검증
CREATE TABLE IF NOT EXISTS copyright_check_results (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL,
    video_title VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    issues JSONB DEFAULT '[]',
    music_detected JSONB DEFAULT '[]',
    monetization_eligible BOOLEAN DEFAULT TRUE,
    platform_checks JSONB DEFAULT '[]',
    checked_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_copyright_check_results_user ON copyright_check_results(user_id);

-- 9. 콘텐츠 번역기
CREATE TABLE IF NOT EXISTS translation_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    video_id            BIGINT NOT NULL,
    video_title         VARCHAR(300) NOT NULL,
    source_language     VARCHAR(10) NOT NULL,
    target_language     VARCHAR(10) NOT NULL,
    content_type        VARCHAR(30) NOT NULL DEFAULT 'ALL',
    original_text       TEXT NOT NULL,
    translated_text     TEXT,
    quality             INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_translation_jobs_user ON translation_jobs(user_id);

CREATE TABLE IF NOT EXISTS translation_glossary (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    source_word         VARCHAR(200) NOT NULL,
    target_word         VARCHAR(200) NOT NULL,
    source_language     VARCHAR(10) NOT NULL,
    target_language     VARCHAR(10) NOT NULL,
    context             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_translation_glossary_user ON translation_glossary(user_id);

-- 10. 소셜 리스닝
CREATE TABLE IF NOT EXISTS brand_mentions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform VARCHAR(30) NOT NULL,
    mention_text TEXT,
    author_name VARCHAR(200),
    author_url VARCHAR(500),
    sentiment VARCHAR(20) DEFAULT 'NEUTRAL',
    reach BIGINT DEFAULT 0,
    source_url VARCHAR(500),
    mentioned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_brand_mentions_user_id ON brand_mentions(user_id);

CREATE TABLE IF NOT EXISTS keyword_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    keyword VARCHAR(200) NOT NULL,
    platforms VARCHAR(500) DEFAULT '[]',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notify_email BOOLEAN DEFAULT FALSE,
    notify_push BOOLEAN DEFAULT TRUE,
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_keyword_alerts_user_id ON keyword_alerts(user_id);
