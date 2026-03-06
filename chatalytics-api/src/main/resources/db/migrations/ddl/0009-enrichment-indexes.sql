CREATE INDEX IF NOT EXISTS idx_message_author_session ON twitch.message (author, session_id);
CREATE INDEX IF NOT EXISTS idx_message_session_timestamp ON twitch.message (session_id, timestamp);
