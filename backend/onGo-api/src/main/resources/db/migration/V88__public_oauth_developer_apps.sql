-- Postiz-compatible developer OAuth2 Authorization Code flow.
-- Secrets, authorization codes, and access tokens are stored only as SHA-256 hashes.
CREATE TABLE IF NOT EXISTS public_oauth_apps (
    id                  BIGSERIAL PRIMARY KEY,
    owner_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id           VARCHAR(80) NOT NULL UNIQUE,
    client_secret_hash  CHAR(64) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    description         VARCHAR(500),
    profile_picture_url TEXT,
    redirect_uri        TEXT NOT NULL,
    revoked_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public_oauth_authorization_codes (
    id             BIGSERIAL PRIMARY KEY,
    app_id         BIGINT NOT NULL REFERENCES public_oauth_apps(id) ON DELETE CASCADE,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash      CHAR(64) NOT NULL UNIQUE,
    redirect_uri   TEXT NOT NULL,
    state          VARCHAR(512),
    expires_at     TIMESTAMP NOT NULL,
    consumed_at    TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_public_oauth_codes_exchange
    ON public_oauth_authorization_codes(code_hash, expires_at)
    WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS public_oauth_tokens (
    id           BIGSERIAL PRIMARY KEY,
    app_id       BIGINT NOT NULL REFERENCES public_oauth_apps(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_prefix VARCHAR(32) NOT NULL,
    token_hash   CHAR(64) NOT NULL UNIQUE,
    revoked_at   TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_public_oauth_tokens_active
    ON public_oauth_tokens(token_hash)
    WHERE revoked_at IS NULL;
