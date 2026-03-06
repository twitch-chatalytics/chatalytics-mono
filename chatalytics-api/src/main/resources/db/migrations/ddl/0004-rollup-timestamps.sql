-- Add timestamp columns
ALTER TABLE twitch.rollup_history
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Create timestamp update function
CREATE OR REPLACE FUNCTION update_timestamp()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
CREATE TRIGGER update_rollup_history_timestamp
    BEFORE UPDATE
    ON twitch.rollup_history
    FOR EACH ROW
EXECUTE FUNCTION update_timestamp();