-- V19: Add default AI provider selection to user settings
ALTER TABLE user_settings
    ADD COLUMN default_ai_provider VARCHAR(20) NOT NULL DEFAULT 'CLAUDE';

ALTER TABLE user_settings
    ADD CONSTRAINT chk_default_ai_provider
        CHECK (default_ai_provider IN ('CLAUDE', 'GEMINI', 'OPENAI'));
