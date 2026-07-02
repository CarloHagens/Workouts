CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    google_sub TEXT UNIQUE,
    google_email TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE device_tokens (
    token TEXT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen TIMESTAMPTZ
);

CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);

ALTER TABLE programs ADD COLUMN user_id BIGINT REFERENCES users(id);
ALTER TABLE workouts ADD COLUMN user_id BIGINT REFERENCES users(id);

-- Rows that predate user accounts all belong to one "legacy" user. Claim it
-- from a device by inserting that device's token (shown in app Settings):
--   INSERT INTO device_tokens (token, user_id) VALUES ('<token>', <legacy id>);
DO $$
DECLARE legacy_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM programs) OR EXISTS (SELECT 1 FROM workouts) THEN
        INSERT INTO users DEFAULT VALUES RETURNING id INTO legacy_id;
        UPDATE programs SET user_id = legacy_id;
        UPDATE workouts SET user_id = legacy_id;
        RAISE NOTICE 'existing data assigned to legacy user id %', legacy_id;
    END IF;
END $$;

ALTER TABLE programs ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE workouts ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX idx_programs_user ON programs(user_id);
CREATE INDEX idx_workouts_user ON workouts(user_id);
