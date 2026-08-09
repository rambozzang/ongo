-- The current team_members columns already support durable invite state.
-- Expiry is derived as invited_at + 7 days and exposed as expiresAt by the API.
COMMENT ON COLUMN team_members.invited_at IS '초대 발송 시각. 초대 유효기간은 발송 후 7일이며 API가 expiresAt으로 노출한다.';
