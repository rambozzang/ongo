-- V95: 댓글 커맨드 센터 - AI 답변 후보 및 위기 감지 설정 테이블

-- AI 댓글 답변 후보 저장 테이블
CREATE TABLE comment_ai_replies (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generated_reply TEXT NOT NULL,
    tone VARCHAR(30) NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comment_ai_replies_user_created ON comment_ai_replies(user_id, created_at DESC);
CREATE INDEX idx_comment_ai_replies_comment ON comment_ai_replies(comment_id);

-- 위기 감지 설정 테이블
CREATE TABLE crisis_detection_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    negative_threshold_pct INT NOT NULL DEFAULT 300,
    window_hours INT NOT NULL DEFAULT 24,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);
