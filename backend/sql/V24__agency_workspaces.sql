-- 에이전시 워크스페이스
CREATE TABLE agency_workspaces (
  id BIGSERIAL PRIMARY KEY,
  owner_user_id BIGINT NOT NULL REFERENCES users(id),
  name VARCHAR(100) NOT NULL,
  description TEXT,
  logo_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE agency_creators (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES agency_workspaces(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  role VARCHAR(20) DEFAULT 'CREATOR',
  joined_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(workspace_id, user_id)
);

CREATE TABLE client_portals (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT NOT NULL REFERENCES agency_workspaces(id),
  client_name VARCHAR(100),
  access_token VARCHAR(64) UNIQUE NOT NULL,
  permissions JSONB DEFAULT '{}',
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
