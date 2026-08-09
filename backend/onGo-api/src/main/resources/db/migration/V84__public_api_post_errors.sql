ALTER TABLE public_api_posts
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(2000);
