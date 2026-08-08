ALTER TABLE recurring_schedules
    ADD COLUMN video_id BIGINT REFERENCES videos(id) ON DELETE CASCADE;

CREATE INDEX idx_recurring_schedules_due
    ON recurring_schedules(is_active, next_run_at)
    WHERE is_active = TRUE AND next_run_at IS NOT NULL;
