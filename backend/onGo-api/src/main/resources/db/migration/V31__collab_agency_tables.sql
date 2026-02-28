-- V31: 협업/에이전시 테이블
-- board, influencer, marketplace, benchmark, milestone, mediakit, agency

-- 1. 협업 보드
CREATE TABLE IF NOT EXISTS board_tasks (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL REFERENCES workspaces(id),
    title           VARCHAR(300)    NOT NULL,
    description     TEXT,
    column_type     VARCHAR(20)     NOT NULL DEFAULT 'IDEA',
    priority        VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)     NOT NULL DEFAULT 'TODO',
    assignee_id     BIGINT          REFERENCES users(id),
    due_date        DATE,
    tags            TEXT[]          DEFAULT '{}',
    attachments     INT             NOT NULL DEFAULT 0,
    video_id        VARCHAR(100),
    order_index     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_board_tasks_workspace ON board_tasks(workspace_id);
CREATE INDEX IF NOT EXISTS idx_board_tasks_assignee ON board_tasks(assignee_id);

CREATE TABLE IF NOT EXISTS board_activities (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL REFERENCES workspaces(id),
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    action          VARCHAR(20)     NOT NULL,
    task_id         BIGINT          REFERENCES board_tasks(id) ON DELETE SET NULL,
    task_title      VARCHAR(300),
    from_column     VARCHAR(20),
    to_column       VARCHAR(20),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_board_activities_workspace ON board_activities(workspace_id);

-- 2. 인플루언서 매칭
CREATE TABLE IF NOT EXISTS influencer_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    channel_name VARCHAR(300) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    subscriber_count BIGINT DEFAULT 0,
    avg_views BIGINT DEFAULT 0,
    engagement_rate NUMERIC(5,2) DEFAULT 0.0,
    categories VARCHAR(500) DEFAULT '[]',
    audience_demographics JSONB DEFAULT '{}',
    contact_email VARCHAR(300),
    profile_url VARCHAR(500),
    match_score INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_influencer_profiles_user_id ON influencer_profiles(user_id);

CREATE TABLE IF NOT EXISTS collab_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    influencer_profile_id BIGINT NOT NULL REFERENCES influencer_profiles(id),
    message TEXT,
    proposed_budget BIGINT DEFAULT 0,
    proposed_type VARCHAR(50) DEFAULT 'COLLABORATION',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    response_message TEXT,
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_collab_requests_user_id ON collab_requests(user_id);

-- 3. 크리에이터 마켓플레이스
CREATE TABLE IF NOT EXISTS marketplace_listings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    creator_name    VARCHAR(200) NOT NULL,
    service_type    VARCHAR(50) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    price           BIGINT NOT NULL DEFAULT 0,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',
    rating          NUMERIC(3,2) NOT NULL DEFAULT 0,
    review_count    INT NOT NULL DEFAULT 0,
    delivery_days   INT NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_listings_user ON marketplace_listings(user_id);

CREATE TABLE IF NOT EXISTS marketplace_orders (
    id              BIGSERIAL PRIMARY KEY,
    listing_id      BIGINT NOT NULL REFERENCES marketplace_listings(id),
    buyer_id        BIGINT NOT NULL REFERENCES users(id),
    seller_id       BIGINT NOT NULL REFERENCES users(id),
    buyer_name      VARCHAR(200) NOT NULL,
    seller_name     VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_price     BIGINT NOT NULL DEFAULT 0,
    order_date      TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivery_date   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mkt_orders_buyer ON marketplace_orders(buyer_id);
CREATE INDEX IF NOT EXISTS idx_mkt_orders_seller ON marketplace_orders(seller_id);

-- 4. 크리에이터 벤치마크
CREATE TABLE IF NOT EXISTS benchmark_results (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    category        VARCHAR(100) NOT NULL,
    my_value        NUMERIC(15,2) DEFAULT 0,
    avg_value       NUMERIC(15,2) DEFAULT 0,
    top_value       NUMERIC(15,2) DEFAULT 0,
    percentile      INT DEFAULT 0,
    metric          VARCHAR(100) NOT NULL,
    trend           VARCHAR(20) DEFAULT 'STABLE',
    updated_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_benchmark_results_workspace ON benchmark_results(workspace_id);

CREATE TABLE IF NOT EXISTS benchmark_peers (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    platform        VARCHAR(50) NOT NULL,
    subscribers     BIGINT DEFAULT 0,
    avg_views       BIGINT DEFAULT 0,
    engagement_rate NUMERIC(5,2) DEFAULT 0,
    category        VARCHAR(100),
    created_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_benchmark_peers_workspace ON benchmark_peers(workspace_id);

-- 5. 크리에이터 마일스톤
CREATE TABLE IF NOT EXISTS creator_milestones (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    type            VARCHAR(20) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    target_value    BIGINT NOT NULL,
    current_value   BIGINT NOT NULL DEFAULT 0,
    progress        NUMERIC(5,2) NOT NULL DEFAULT 0,
    platform        VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    achieved_at     TIMESTAMPTZ,
    target_date     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_creator_milestones_workspace ON creator_milestones(workspace_id);

-- 6. 미디어 킷
CREATE TABLE IF NOT EXISTS media_kits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    template_style VARCHAR(30) NOT NULL DEFAULT 'MODERN',
    bio TEXT,
    profile_image_url VARCHAR(500),
    platforms JSONB DEFAULT '[]',
    demographics JSONB DEFAULT '[]',
    top_content JSONB DEFAULT '[]',
    campaign_results JSONB DEFAULT '[]',
    rate_cards JSONB DEFAULT '[]',
    contact_email VARCHAR(300),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_media_kits_user_id ON media_kits(user_id);

-- 7. 에이전시 워크스페이스
CREATE TABLE IF NOT EXISTS agency_workspaces (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    logo_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agency_creators (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES agency_workspaces(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) DEFAULT 'CREATOR',
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(workspace_id, user_id)
);

CREATE TABLE IF NOT EXISTS client_portals (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES agency_workspaces(id),
    client_name VARCHAR(100),
    access_token VARCHAR(64) UNIQUE NOT NULL,
    permissions JSONB DEFAULT '{}',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
