-- V36: Change default AI provider to QWEN
ALTER TABLE user_settings
    ALTER COLUMN default_ai_provider SET DEFAULT 'QWEN';
