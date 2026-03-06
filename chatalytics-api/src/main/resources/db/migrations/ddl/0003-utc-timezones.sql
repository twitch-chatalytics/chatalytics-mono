BEGIN;

-----------------------------------------------------------
-- 1) twitch.user.created_at
-----------------------------------------------------------
-- Remove existing default if necessary
ALTER TABLE twitch.user
    ALTER COLUMN created_at
        DROP DEFAULT;

-- Convert to timestamptz, forcing existing data to be interpreted as UTC
ALTER TABLE twitch.user
    ALTER COLUMN created_at
        TYPE timestamptz(0)
        USING created_at AT TIME ZONE 'UTC';

-- Set new default that explicitly stores in UTC
ALTER TABLE twitch.user
    ALTER COLUMN created_at
        SET DEFAULT (CURRENT_TIMESTAMP(0) AT TIME ZONE 'UTC');


-----------------------------------------------------------
-- 2) twitch.session.start_time
-----------------------------------------------------------
ALTER TABLE twitch.session
    ALTER COLUMN start_time
        DROP DEFAULT;

ALTER TABLE twitch.session
    ALTER COLUMN start_time
        TYPE timestamptz(0)
        USING start_time AT TIME ZONE 'UTC';

ALTER TABLE twitch.session
    ALTER COLUMN start_time
        SET DEFAULT (CURRENT_TIMESTAMP(0) AT TIME ZONE 'UTC');


-----------------------------------------------------------
-- 3) twitch.session.end_time
-----------------------------------------------------------
ALTER TABLE twitch.session
    ALTER COLUMN end_time
        TYPE timestamptz(0)
        USING end_time AT TIME ZONE 'UTC';

-----------------------------------------------------------
-- 4) twitch.message.timestamp
-----------------------------------------------------------
ALTER TABLE twitch.message
    ALTER COLUMN "timestamp"
        TYPE timestamptz(0)
        USING "timestamp" AT TIME ZONE 'UTC';

COMMIT;
