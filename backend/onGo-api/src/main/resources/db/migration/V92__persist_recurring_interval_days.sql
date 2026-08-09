ALTER TABLE recurring_schedules
    ADD COLUMN IF NOT EXISTS interval_days INT;

ALTER TABLE recurring_schedules
    DROP CONSTRAINT IF EXISTS recurring_schedules_interval_days_check;

ALTER TABLE recurring_schedules
    ADD CONSTRAINT recurring_schedules_interval_days_check
    CHECK (interval_days IS NULL OR interval_days BETWEEN 1 AND 365);
