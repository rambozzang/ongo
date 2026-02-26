-- Wave 14: 팬 커뮤니티
CREATE TABLE community_posts (
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

CREATE TABLE post_attachments (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    type            VARCHAR(10) NOT NULL,
    url             TEXT NOT NULL,
    name            VARCHAR(200),
    size            BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE post_comments (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    author_name     VARCHAR(100) NOT NULL,
    author_avatar   TEXT,
    is_creator      BOOLEAN NOT NULL DEFAULT false,
    content         TEXT NOT NULL,
    likes           INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_community_posts_workspace ON community_posts(workspace_id);
CREATE INDEX idx_community_posts_type ON community_posts(workspace_id, type);
CREATE INDEX idx_community_posts_pinned ON community_posts(workspace_id, is_pinned);
CREATE INDEX idx_post_attachments_post ON post_attachments(post_id);
CREATE INDEX idx_post_comments_post ON post_comments(post_id);
