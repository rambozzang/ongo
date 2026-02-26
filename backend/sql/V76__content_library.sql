-- 콘텐츠 라이브러리
CREATE TABLE library_folders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    parent_id       BIGINT REFERENCES library_folders(id) ON DELETE SET NULL,
    color           VARCHAR(20) NOT NULL DEFAULT '#3B82F6',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE library_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(500) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    platform        VARCHAR(50),
    thumbnail_url   VARCHAR(1000),
    file_size       BIGINT NOT NULL DEFAULT 0,
    tags            JSONB NOT NULL DEFAULT '[]',
    folder_id       BIGINT REFERENCES library_folders(id) ON DELETE SET NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_library_folders_user ON library_folders(user_id);
CREATE INDEX idx_library_items_user ON library_items(user_id);
CREATE INDEX idx_library_items_folder ON library_items(folder_id);
CREATE INDEX idx_library_items_type ON library_items(type);
