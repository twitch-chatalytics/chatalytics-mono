-- First, create the function that will handle the consolidation
CREATE OR REPLACE FUNCTION consolidate_session_summary()
    RETURNS TRIGGER AS $$
BEGIN
    -- Only proceed if is_partial is changing from true to false
    IF OLD.is_partial = true AND NEW.is_partial = false THEN
        -- Insert a new consolidated row
        INSERT INTO session_summary (
            twitch_id,
            session_id,
            messages_per_minute,
            total_chatters,
            total_messages,
            total_sessions,
            mentions,
            is_partial,
            top_chatter_by_message_count,
            top_chatter_by_message_count_value
        )
        SELECT
            twitch_id,
            session_id,
            AVG(messages_per_minute),
            SUM(total_chatters),
            SUM(total_messages),
            SUM(total_sessions),
            SUM(mentions),
            false,
            -- For top chatter, we'll take the one with the highest message count
            FIRST_VALUE(top_chatter_by_message_count) OVER (
                ORDER BY top_chatter_by_message_count_value DESC
                ),
            MAX(top_chatter_by_message_count_value)
        FROM twitch.session_summary
        WHERE session_id = NEW.session_id
          AND is_partial = true
        GROUP BY twitch_id, session_id;

        -- Delete all partial records for this session
        DELETE FROM twitch.session_summary
        WHERE session_id = NEW.session_id
          AND is_partial = true;

        -- Prevent the current update (since we've created a new consolidated record)
        RETURN NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create the trigger
CREATE TRIGGER session_summary_consolidation
    BEFORE UPDATE ON twitch.session_summary
    FOR EACH ROW
EXECUTE FUNCTION consolidate_session_summary();