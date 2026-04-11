ALTER TABLE exercise_settings RENAME COLUMN deload_amount TO deload_percent;
ALTER TABLE exercise_settings ALTER COLUMN deload_percent SET DEFAULT 10.0;
