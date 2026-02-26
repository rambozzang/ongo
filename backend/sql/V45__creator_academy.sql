CREATE TABLE IF NOT EXISTS academy_courses (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    category VARCHAR(30) NOT NULL DEFAULT 'GROWTH',
    level VARCHAR(30) NOT NULL DEFAULT 'BEGINNER',
    instructor_name VARCHAR(200) NOT NULL,
    instructor_avatar VARCHAR(500),
    thumbnail_url VARCHAR(500),
    total_lessons INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0,
    rating NUMERIC(3,2) DEFAULT 0.0,
    enrolled_count INT NOT NULL DEFAULT 0,
    credit_reward INT NOT NULL DEFAULT 0,
    tags VARCHAR(500) DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_academy_courses_category ON academy_courses(category);
CREATE INDEX IF NOT EXISTS idx_academy_courses_level ON academy_courses(level);

CREATE TABLE IF NOT EXISTS academy_lessons (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES academy_courses(id) ON DELETE CASCADE,
    order_number INT NOT NULL DEFAULT 1,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    video_url VARCHAR(500),
    duration INT NOT NULL DEFAULT 0,
    resources JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_academy_lessons_course_id ON academy_lessons(course_id);

CREATE TABLE IF NOT EXISTS academy_enrollments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    course_id BIGINT NOT NULL REFERENCES academy_courses(id),
    completed_lessons INT NOT NULL DEFAULT 0,
    last_lesson_id BIGINT,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE(user_id, course_id)
);
CREATE INDEX IF NOT EXISTS idx_academy_enrollments_user_id ON academy_enrollments(user_id);
CREATE INDEX IF NOT EXISTS idx_academy_enrollments_course_id ON academy_enrollments(course_id);

CREATE TABLE IF NOT EXISTS academy_lesson_completions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    course_id BIGINT NOT NULL REFERENCES academy_courses(id),
    lesson_id BIGINT NOT NULL REFERENCES academy_lessons(id),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, lesson_id)
);
CREATE INDEX IF NOT EXISTS idx_academy_lesson_completions_user_id ON academy_lesson_completions(user_id);
