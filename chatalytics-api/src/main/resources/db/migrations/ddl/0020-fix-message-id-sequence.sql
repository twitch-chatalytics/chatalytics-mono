-- Fix: recreate message_id_seq dropped by CASCADE in 0018-partition-message-table.sql
-- The DROP TABLE message_old CASCADE also dropped the sequence owned by message_old.id,
-- leaving the partitioned message table with no DEFAULT on id.

-- Create sequence, set default, and assign ownership (no SELECT on message table here
-- to avoid lock conflicts with ALTER TABLE in the same transaction)
CREATE SEQUENCE IF NOT EXISTS twitch.message_id_seq;
ALTER TABLE twitch.message ALTER COLUMN id SET DEFAULT nextval('twitch.message_id_seq');
ALTER SEQUENCE twitch.message_id_seq OWNED BY twitch.message.id;
