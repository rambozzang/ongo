-- V14__workspaces.sql

-- 워크스페이스 테이블
CREATE TABLE workspaces (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    logo_url    VARCHAR(500),
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_workspaces_slug ON workspaces(slug);
CREATE INDEX idx_workspaces_owner ON workspaces(owner_id);

-- team_members에 workspace_id 컬럼 추가
ALTER TABLE team_members ADD COLUMN IF NOT EXISTS workspace_id BIGINT REFERENCES workspaces(id);
CREATE INDEX idx_team_members_workspace ON team_members(workspace_id);

-- 기존 사용자별 기본 워크스페이스 생성
INSERT INTO workspaces (owner_id, name, slug)
SELECT id, COALESCE(nickname, name, email), CONCAT('ws-', id)
FROM users
ON CONFLICT DO NOTHING;

-- 기존 team_members에 workspace_id 설정 (사용자의 기본 워크스페이스 연결)
UPDATE team_members tm
SET workspace_id = w.id
FROM workspaces w
WHERE tm.user_id = w.owner_id
  AND tm.workspace_id IS NULL;
