-- Ideas
CREATE TABLE ideas (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'BACKLOG',
    category VARCHAR(50),
    tags TEXT[] DEFAULT '{}',
    priority VARCHAR(10) DEFAULT 'MEDIUM',
    source VARCHAR(50),
    reference_url VARCHAR(500),
    due_date DATE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_ideas_user_id ON ideas(user_id);
CREATE INDEX idx_ideas_status ON ideas(user_id, status);

-- Goals
CREATE TABLE goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    metric_type VARCHAR(30) NOT NULL,
    target_value BIGINT NOT NULL,
    current_value BIGINT DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_goals_user_id ON goals(user_id);

-- Goal Milestones
CREATE TABLE goal_milestones (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    target_value BIGINT NOT NULL,
    is_reached BOOLEAN DEFAULT FALSE,
    reached_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_goal_milestones_goal_id ON goal_milestones(goal_id);

-- Brand Kits
CREATE TABLE brand_kits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    primary_color VARCHAR(7) DEFAULT '#7c3aed',
    secondary_color VARCHAR(7) DEFAULT '#1e40af',
    accent_color VARCHAR(7) DEFAULT '#059669',
    font_family VARCHAR(100) DEFAULT 'Pretendard',
    logo_url VARCHAR(500),
    intro_template_url VARCHAR(500),
    outro_template_url VARCHAR(500),
    watermark_url VARCHAR(500),
    guidelines TEXT,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_brand_kits_user_id ON brand_kits(user_id);

-- Link-in-bio Pages
CREATE TABLE link_bio_pages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    slug VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200),
    bio TEXT,
    avatar_url VARCHAR(500),
    theme VARCHAR(30) DEFAULT 'default',
    background_color VARCHAR(7) DEFAULT '#ffffff',
    text_color VARCHAR(7) DEFAULT '#000000',
    is_published BOOLEAN DEFAULT FALSE,
    view_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_link_bio_pages_user_id ON link_bio_pages(user_id);
CREATE UNIQUE INDEX idx_link_bio_pages_slug ON link_bio_pages(slug);

-- Link-in-bio Links
CREATE TABLE link_bio_links (
    id BIGSERIAL PRIMARY KEY,
    page_id BIGINT NOT NULL REFERENCES link_bio_pages(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    icon VARCHAR(50),
    sort_order INT DEFAULT 0,
    click_count BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_link_bio_links_page_id ON link_bio_links(page_id);
