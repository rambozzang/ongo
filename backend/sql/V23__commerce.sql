-- 소셜 커머스
CREATE TABLE commerce_platforms (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  platform_type VARCHAR(30) NOT NULL,
  platform_name VARCHAR(100),
  access_token_enc TEXT,
  status VARCHAR(20) DEFAULT 'CONNECTED',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE commerce_products (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  commerce_platform_id BIGINT REFERENCES commerce_platforms(id),
  product_name VARCHAR(200),
  product_url TEXT,
  image_url TEXT,
  price DECIMAL(12,2),
  affiliate_link TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE product_video_links (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES commerce_products(id),
  video_id BIGINT NOT NULL REFERENCES videos(id),
  clicks INTEGER DEFAULT 0,
  conversions INTEGER DEFAULT 0,
  revenue DECIMAL(12,2) DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(product_id, video_id)
);
