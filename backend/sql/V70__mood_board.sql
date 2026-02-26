-- 크리에이터 무드보드
CREATE TABLE IF NOT EXISTS mood_boards (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    item_count          INT DEFAULT 0,
    cover_image         VARCHAR(1000),
    tags                JSONB DEFAULT '[]',
    is_public           BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_mood_boards_workspace ON mood_boards(workspace_id);

-- 무드보드 아이템
CREATE TABLE IF NOT EXISTS mood_board_items (
    id                  BIGSERIAL PRIMARY KEY,
    board_id            BIGINT NOT NULL REFERENCES mood_boards(id) ON DELETE CASCADE,
    type                VARCHAR(50) NOT NULL,
    title               VARCHAR(300),
    image_url           VARCHAR(1000),
    source_url          VARCHAR(1000),
    note                TEXT,
    color               VARCHAR(20),
    position            INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_mood_board_items_board ON mood_board_items(board_id);
