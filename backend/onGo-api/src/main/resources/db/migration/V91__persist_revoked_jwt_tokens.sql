-- JWT access-token revocations must survive process restarts and work across
-- application instances without introducing an external cache dependency.
CREATE TABLE IF NOT EXISTS revoked_jwt_tokens (
    jti         VARCHAR(255) PRIMARY KEY,
    expires_at  TIMESTAMP NOT NULL,
    revoked_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_revoked_jwt_tokens_expires_at
    ON revoked_jwt_tokens(expires_at);
