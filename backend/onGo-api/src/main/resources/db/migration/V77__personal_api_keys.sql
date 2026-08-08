-- Personal API keys for automation clients (N8N/Make/Zapier and scripts).
-- Only a SHA-256 digest is stored; the raw secret is returned exactly once.
CREATE TABLE IF NOT EXISTS api_keys (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    key_prefix  VARCHAR(32) NOT NULL,
    key_hash    VARCHAR(64) NOT NULL UNIQUE,
    last_used_at TIMESTAMP NULL,
    expires_at  TIMESTAMP NULL,
    revoked_at  TIMESTAMP NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_active_hash ON api_keys(key_hash)
    WHERE revoked_at IS NULL;
