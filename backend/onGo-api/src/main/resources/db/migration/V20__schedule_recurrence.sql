-- 예약 반복(recurrence) 지원을 위한 컬럼 추가
ALTER TABLE schedules ADD COLUMN recurrence_type VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE schedules ADD COLUMN recurrence_interval INTEGER DEFAULT 1;
ALTER TABLE schedules ADD COLUMN recurrence_days_of_week JSONB;
ALTER TABLE schedules ADD COLUMN recurrence_end_date DATE;
ALTER TABLE schedules ADD COLUMN recurrence_max_occurrences INTEGER;
ALTER TABLE schedules ADD COLUMN parent_schedule_id BIGINT REFERENCES schedules(id);

-- 반복 원본 조회를 위한 인덱스
CREATE INDEX idx_schedules_parent_id ON schedules(parent_schedule_id) WHERE parent_schedule_id IS NOT NULL;
CREATE INDEX idx_schedules_recurrence_type ON schedules(recurrence_type) WHERE recurrence_type != 'NONE';
