-- 크리에이터 포트폴리오 빌더
CREATE TABLE portfolios (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    template        VARCHAR(30) NOT NULL DEFAULT 'MINIMAL',
    theme           VARCHAR(50) NOT NULL DEFAULT 'light',
    is_published    BOOLEAN NOT NULL DEFAULT false,
    public_url      VARCHAR(500),
    view_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE portfolio_sections (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    section_type    VARCHAR(30) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL DEFAULT '',
    sort_order      INT NOT NULL DEFAULT 0,
    is_visible      BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_portfolios_user ON portfolios(user_id);
CREATE INDEX idx_portfolios_published ON portfolios(is_published);
CREATE INDEX idx_portfolio_sections_portfolio ON portfolio_sections(portfolio_id);
