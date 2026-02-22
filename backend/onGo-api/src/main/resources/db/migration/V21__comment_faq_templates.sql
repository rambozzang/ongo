-- FAQ 자동 분류 템플릿 테이블
CREATE TABLE comment_faq_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    topic VARCHAR(200) NOT NULL,
    template_reply TEXT NOT NULL,
    usage_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comment_faq_templates_user ON comment_faq_templates(user_id);
