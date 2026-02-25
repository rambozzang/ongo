-- AI 콘텐츠 스튜디오
CREATE TABLE content_clips (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  source_video_id BIGINT REFERENCES videos(id),
  title VARCHAR(200),
  start_time_ms BIGINT NOT NULL,
  end_time_ms BIGINT NOT NULL,
  aspect_ratio VARCHAR(10) DEFAULT '9:16',
  status VARCHAR(20) DEFAULT 'PENDING',
  output_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE video_captions (
  id BIGSERIAL PRIMARY KEY,
  video_id BIGINT NOT NULL REFERENCES videos(id),
  language VARCHAR(10) DEFAULT 'ko',
  caption_data JSONB NOT NULL,
  status VARCHAR(20) DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE ai_thumbnails (
  id BIGSERIAL PRIMARY KEY,
  video_id BIGINT NOT NULL REFERENCES videos(id),
  style VARCHAR(50),
  text_overlay TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
