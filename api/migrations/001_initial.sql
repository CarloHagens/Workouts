CREATE TABLE exercises (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    category TEXT NOT NULL,
    muscle_group TEXT NOT NULL
);

CREATE TABLE programs (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE program_exercises (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id),
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE(program_id, exercise_id)
);

CREATE TABLE workouts (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES programs(id),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    duration_seconds INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE workout_sets (
    id BIGSERIAL PRIMARY KEY,
    workout_id BIGINT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id),
    set_order INT NOT NULL,
    reps INT NOT NULL,
    weight NUMERIC(7,2) NOT NULL
);

CREATE INDEX idx_program_exercises_program ON program_exercises(program_id);
CREATE INDEX idx_workouts_program ON workouts(program_id);
CREATE INDEX idx_workout_sets_workout ON workout_sets(workout_id);
