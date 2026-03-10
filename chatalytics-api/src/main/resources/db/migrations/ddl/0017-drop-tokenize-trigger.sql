-- Step 1: Drop the synchronous tokenization trigger (critical bottleneck at scale)
-- The fn_tokenize_message_word() function is kept for reference by the async job,
-- but the trigger that fires on every INSERT is removed.

DROP TRIGGER IF EXISTS tokenize_message_text ON twitch.message;

-- Add column to track which messages have been tokenized by the async job
ALTER TABLE twitch.message ADD COLUMN IF NOT EXISTS tokenized_at TIMESTAMPTZ;

-- Index to efficiently find un-tokenized messages
CREATE INDEX IF NOT EXISTS idx_message_tokenized_at_null
    ON twitch.message (id) WHERE tokenized_at IS NULL;

-- Backfill: mark all existing messages as already tokenized (they were processed by the trigger)
UPDATE twitch.message SET tokenized_at = NOW() WHERE tokenized_at IS NULL;
