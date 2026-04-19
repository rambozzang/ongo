-- V39: 키워드 리서치 이력 테이블
CREATE TABLE IF NOT EXISTS keyword_research_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    keyword     VARCHAR(200) NOT NULL,
    platforms   VARCHAR(500) NOT NULL,
    result_json TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_keyword_research_history_user ON keyword_research_history(user_id);
CREATE INDEX IF NOT EXISTS idx_keyword_research_history_created ON keyword_research_history(created_at DESC);
