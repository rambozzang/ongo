-- 팀 초대와 워크스페이스 접근을 연결한다.
-- V22에서 추가된 nullable workspace_id를 기존 owner의 최초 워크스페이스로
-- 채워, 기존 초대도 수락 후 동일한 워크스페이스에 접근할 수 있게 한다.
WITH owner_workspace AS (
    SELECT DISTINCT ON (owner_id) id, owner_id
    FROM workspaces
    ORDER BY owner_id, created_at ASC, id ASC
)
UPDATE team_members tm
SET workspace_id = ow.id
FROM owner_workspace ow
WHERE tm.workspace_id IS NULL
  AND tm.user_id = ow.owner_id;

CREATE INDEX IF NOT EXISTS idx_team_members_workspace_status
    ON team_members(workspace_id, status);
