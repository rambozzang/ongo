-- Postiz calls the workspace/customer scope a group. Keep the relation
-- durable so public API filters cannot accidentally mix tenant data.
ALTER TABLE channels
    ADD COLUMN IF NOT EXISTS workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL;

ALTER TABLE public_api_posts
    ADD COLUMN IF NOT EXISTS workspace_id BIGINT REFERENCES workspaces(id) ON DELETE SET NULL;

-- Existing user-owned rows belong to that user's first workspace. Users with
-- no workspace remain nullable and are still isolated by user_id.
WITH owner_workspace AS (
    SELECT DISTINCT ON (owner_id) owner_id, id
    FROM workspaces
    ORDER BY owner_id, created_at, id
)
UPDATE channels c
SET workspace_id = ow.id
FROM owner_workspace ow
WHERE c.user_id = ow.owner_id
  AND c.workspace_id IS NULL;

WITH owner_workspace AS (
    SELECT DISTINCT ON (owner_id) owner_id, id
    FROM workspaces
    ORDER BY owner_id, created_at, id
)
UPDATE public_api_posts p
SET workspace_id = ow.id
FROM owner_workspace ow
WHERE p.user_id = ow.owner_id
  AND p.workspace_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_channels_user_workspace
    ON channels(user_id, workspace_id, connected_at DESC);
CREATE INDEX IF NOT EXISTS idx_public_api_posts_user_workspace
    ON public_api_posts(user_id, workspace_id, created_at DESC);
