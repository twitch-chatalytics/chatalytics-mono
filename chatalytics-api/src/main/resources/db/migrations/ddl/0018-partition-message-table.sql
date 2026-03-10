-- Step 4: Partition twitch.message by twitch_id (hash, 32 partitions)
-- At scale (~69GB/day), partitioning enables parallel scans and partition pruning.
--
-- Strategy: rename old table, create partitioned table, migrate data, drop old table.
-- This is safe for small existing datasets.

-- 1. Rename existing table
ALTER TABLE twitch.message RENAME TO message_old;

-- 2. Drop foreign key constraints referencing message (if any exist on message_word)
ALTER TABLE twitch.message_word DROP CONSTRAINT IF EXISTS message_word_message_id_fkey;

-- 3. Drop the old foreign key constraints on message_old
ALTER TABLE twitch.message_old DROP CONSTRAINT IF EXISTS message_twitch_id_fkey;
ALTER TABLE twitch.message_old DROP CONSTRAINT IF EXISTS message_session_id_fkey;

-- 3b. Drop old indexes (they follow the renamed table but keep their names)
DROP INDEX IF EXISTS twitch.idx_message_author;
DROP INDEX IF EXISTS twitch.idx_message_twitch_id;
DROP INDEX IF EXISTS twitch.idx_message_session_id;
DROP INDEX IF EXISTS twitch.idx_message_twitch_session;
DROP INDEX IF EXISTS twitch.idx_message_timestamp;
DROP INDEX IF EXISTS twitch.idx_message_id;
DROP INDEX IF EXISTS twitch.idx_message_tokenized_at_null;
DROP INDEX IF EXISTS twitch.idx_message_processed_text;

-- 4. Create new partitioned table (partitioned tables cannot have global PKs with FKs)
CREATE TABLE twitch.message
(
    id           bigint NOT NULL DEFAULT nextval('twitch.message_id_seq'),
    twitch_id    bigint NOT NULL,
    message_text TEXT   NOT NULL,
    timestamp    TIMESTAMPTZ,
    session_id   bigint NOT NULL,
    author       TEXT   NOT NULL,
    tokenized_at TIMESTAMPTZ
) PARTITION BY HASH (twitch_id);

-- 5. Create 32 hash partitions
DO $$
BEGIN
    FOR i IN 0..31 LOOP
        EXECUTE format(
            'CREATE TABLE twitch.message_p%s PARTITION OF twitch.message FOR VALUES WITH (MODULUS 32, REMAINDER %s)',
            i, i
        );
    END LOOP;
END $$;

-- 6. Recreate indexes on partitioned table (automatically propagated to partitions)
CREATE INDEX idx_message_author ON twitch.message (author);
CREATE INDEX idx_message_twitch_id ON twitch.message (twitch_id);
CREATE INDEX idx_message_session_id ON twitch.message (session_id);
CREATE INDEX idx_message_twitch_session ON twitch.message (twitch_id, session_id);
CREATE INDEX idx_message_timestamp ON twitch.message (timestamp);
CREATE INDEX idx_message_id ON twitch.message (id);
CREATE INDEX idx_message_tokenized_at_null ON twitch.message (id) WHERE tokenized_at IS NULL;

-- 7. Migrate existing data
INSERT INTO twitch.message (id, twitch_id, message_text, timestamp, session_id, author, tokenized_at)
SELECT id, twitch_id, message_text, timestamp, session_id, author, tokenized_at
FROM twitch.message_old;

-- 8. Update sequence to continue from max id
SELECT setval('twitch.message_id_seq', COALESCE((SELECT MAX(id) FROM twitch.message), 1));

-- 9. Drop old table (CASCADE to remove dependent objects like old FK references)
DROP TABLE twitch.message_old CASCADE;

-- 10. PostgreSQL tuning recommendations (apply via ConfigMap or postgresql.conf):
-- shared_buffers = 4GB
-- wal_buffers = 256MB
-- synchronous_commit = off  (Kafka provides durability)
-- checkpoint_completion_target = 0.9
-- effective_cache_size = 12GB
-- work_mem = 512MB (already set in 0000-init-schema.sql)
