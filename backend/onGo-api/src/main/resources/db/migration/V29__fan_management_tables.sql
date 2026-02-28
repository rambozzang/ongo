-- V29: 팬 관리 테이블
-- community, poll, reward, comment_summary, smart_reply

-- 1. 팬 커뮤니티
CREATE TABLE IF NOT EXISTS community_posts (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    type            VARCHAR(20) NOT NULL DEFAULT 'DISCUSSION',
    title           VARCHAR(300) NOT NULL,
    content         TEXT NOT NULL,
    author_name     VARCHAR(100) NOT NULL,
    author_avatar   TEXT,
    is_creator      BOOLEAN NOT NULL DEFAULT false,
    likes           INT NOT NULL DEFAULT 0,
    comments_count  INT NOT NULL DEFAULT 0,
    shares          INT NOT NULL DEFAULT 0,
    is_pinned       BOOLEAN NOT NULL DEFAULT false,
    tags            JSONB NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_community_posts_workspace ON community_posts(workspace_id);

CREATE TABLE IF NOT EXISTS post_comments (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    author_name     VARCHAR(100) NOT NULL,
    author_avatar   TEXT,
    is_creator      BOOLEAN NOT NULL DEFAULT false,
    content         TEXT NOT NULL,
    likes           INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_post_comments_post ON post_comments(post_id);

-- 2. 팬 투표
CREATE TABLE IF NOT EXISTS fan_polls (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    type            VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_votes     INT NOT NULL DEFAULT 0,
    start_date      DATE,
    end_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fan_polls_user ON fan_polls(user_id);
CREATE INDEX IF NOT EXISTS idx_fan_polls_status ON fan_polls(status);

CREATE TABLE IF NOT EXISTS poll_options (
    id              BIGSERIAL PRIMARY KEY,
    poll_id         BIGINT NOT NULL REFERENCES fan_polls(id) ON DELETE CASCADE,
    text            VARCHAR(500) NOT NULL,
    votes           INT NOT NULL DEFAULT 0,
    percentage      NUMERIC(5,2) NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_poll_options_poll ON poll_options(poll_id);

-- 3. 팬 리워드
CREATE TABLE IF NOT EXISTS fan_rewards (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    points_cost     INT NOT NULL DEFAULT 0,
    category        VARCHAR(50) NOT NULL DEFAULT 'BADGE',
    is_active       BOOLEAN NOT NULL DEFAULT true,
    claimed_count   INT NOT NULL DEFAULT 0,
    max_claims      INT,
    image_url       VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fan_rewards_user ON fan_rewards(user_id);

CREATE TABLE IF NOT EXISTS fan_activities (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    fan_name        VARCHAR(200) NOT NULL,
    activity_type   VARCHAR(50) NOT NULL,
    points          INT NOT NULL DEFAULT 0,
    description     TEXT,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fan_activities_user ON fan_activities(user_id);

-- 4. AI 댓글 요약
CREATE TABLE IF NOT EXISTS comment_summary_results (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    video_title         VARCHAR(500) NOT NULL,
    platform            VARCHAR(50) NOT NULL,
    total_comments      INT DEFAULT 0,
    positive_pct        INT DEFAULT 0,
    negative_pct        INT DEFAULT 0,
    neutral_pct         INT DEFAULT 0,
    top_topics          JSONB DEFAULT '[]',
    ai_summary          TEXT,
    analyzed_at         TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_comment_summary_workspace ON comment_summary_results(workspace_id);

CREATE TABLE IF NOT EXISTS top_comments (
    id                  BIGSERIAL PRIMARY KEY,
    summary_id          BIGINT NOT NULL REFERENCES comment_summary_results(id) ON DELETE CASCADE,
    author              VARCHAR(200) NOT NULL,
    text                TEXT NOT NULL,
    likes               INT DEFAULT 0,
    sentiment           VARCHAR(30) DEFAULT 'NEUTRAL',
    is_highlighted      BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_top_comments_summary ON top_comments(summary_id);

-- 5. 스마트 답장
CREATE TABLE IF NOT EXISTS smart_reply_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    context VARCHAR(30) NOT NULL DEFAULT 'COMMENT',
    trigger_keywords VARCHAR(500) DEFAULT '[]',
    sentiment VARCHAR(20),
    tone VARCHAR(30) NOT NULL DEFAULT 'FRIENDLY',
    template_text TEXT,
    use_ai BOOLEAN NOT NULL DEFAULT FALSE,
    auto_send BOOLEAN NOT NULL DEFAULT FALSE,
    platform VARCHAR(30),
    reply_count INT NOT NULL DEFAULT 0,
    last_used TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smart_reply_rules_user_id ON smart_reply_rules(user_id);

CREATE TABLE IF NOT EXISTS smart_reply_suggestions (
    id VARCHAR(100) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id BIGINT NOT NULL REFERENCES users(id),
    original_text TEXT NOT NULL,
    original_author VARCHAR(200),
    platform VARCHAR(30) NOT NULL,
    context VARCHAR(30) NOT NULL DEFAULT 'COMMENT',
    sentiment VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL',
    suggestions JSONB DEFAULT '[]',
    video_id VARCHAR(100),
    video_title VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smart_reply_suggestions_user_id ON smart_reply_suggestions(user_id);

CREATE TABLE IF NOT EXISTS smart_reply_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    default_tone VARCHAR(30) NOT NULL DEFAULT 'FRIENDLY',
    enable_auto_reply BOOLEAN NOT NULL DEFAULT FALSE,
    max_auto_replies_per_day INT NOT NULL DEFAULT 50,
    exclude_keywords VARCHAR(500) DEFAULT '[]',
    reply_delay INT NOT NULL DEFAULT 0,
    platforms VARCHAR(500) DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
