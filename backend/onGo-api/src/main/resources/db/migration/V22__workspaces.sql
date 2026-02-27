-- Workspaces
CREATE TABLE workspaces (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    logo_url    VARCHAR(500),
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_workspaces_owner_id ON workspaces(owner_id);
CREATE UNIQUE INDEX idx_workspaces_slug ON workspaces(slug);

-- Add workspace_id to team_members
ALTER TABLE team_members ADD COLUMN workspace_id BIGINT REFERENCES workspaces(id);
CREATE INDEX idx_team_members_workspace_id ON team_members(workspace_id);
