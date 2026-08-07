ALTER TABLE ab_tests
    ADD COLUMN IF NOT EXISTS duration_hours INTEGER;

ALTER TABLE ab_tests
    DROP CONSTRAINT IF EXISTS ab_tests_duration_hours_check;

ALTER TABLE ab_tests
    ADD CONSTRAINT ab_tests_duration_hours_check
    CHECK (duration_hours IS NULL OR duration_hours BETWEEN 1 AND 168);
