-- V35: Expand AI provider constraint to include DashScope models
ALTER TABLE user_settings
    DROP CONSTRAINT chk_default_ai_provider;

ALTER TABLE user_settings
    ADD CONSTRAINT chk_default_ai_provider
        CHECK (default_ai_provider IN ('CLAUDE', 'GEMINI', 'OPENAI', 'QWEN', 'KIMI', 'GLM', 'MINIMAX'));
