ALTER TABLE twitch.session_summary
    ADD COLUMN is_partial boolean DEFAULT false;
