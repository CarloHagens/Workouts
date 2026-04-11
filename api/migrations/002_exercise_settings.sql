CREATE TABLE exercise_settings (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id),
    working_weight NUMERIC(7,2) NOT NULL DEFAULT 0,
    target_reps INT NOT NULL DEFAULT 10,
    target_sets INT NOT NULL DEFAULT 3,
    increment_amount NUMERIC(7,2) NOT NULL DEFAULT 2.5,
    deload_amount NUMERIC(7,2) NOT NULL DEFAULT 5.0,
    success_threshold INT NOT NULL DEFAULT 3,
    failure_threshold INT NOT NULL DEFAULT 2,
    consecutive_successes INT NOT NULL DEFAULT 0,
    consecutive_failures INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(program_id, exercise_id)
);

CREATE INDEX idx_exercise_settings_program ON exercise_settings(program_id);
