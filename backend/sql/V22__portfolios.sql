-- 크리에이터 포트폴리오
CREATE TABLE creator_portfolios (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) UNIQUE,
  display_name VARCHAR(100),
  bio TEXT,
  category VARCHAR(50),
  profile_image_url TEXT,
  theme VARCHAR(30) DEFAULT 'default',
  public_slug VARCHAR(50) UNIQUE,
  showcase_videos JSONB DEFAULT '[]',
  brand_history JSONB DEFAULT '[]',
  is_public BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
