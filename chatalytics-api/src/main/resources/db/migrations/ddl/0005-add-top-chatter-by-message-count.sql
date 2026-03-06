ALTER TABLE twitch.session_summary
    ADD COLUMN top_chatter_by_message_count       bigint,
    ADD COLUMN top_chatter_by_message_count_value bigint;
