--liquibase formatted sql
--changeset platform-abstraction:1

-- 1. Rename schema twitch → chat
ALTER SCHEMA twitch RENAME TO chat;

-- 2. Rename twitch_id → channel_id on non-partitioned tables
ALTER TABLE chat.session RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.message_word RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.session_summary RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.stream_snapshot RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.stream_recap RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.session_authenticity RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.channel_authenticity RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.channel_brand_safety RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.alert_rule RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.alert_event RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.campaign RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.viewer_api_key RENAME COLUMN viewer_twitch_id TO viewer_channel_id;
ALTER TABLE chat.socialblade_channel RENAME COLUMN twitch_id TO channel_id;
ALTER TABLE chat.socialblade_daily RENAME COLUMN twitch_id TO channel_id;

-- 3. Rename twitch_id on partitioned message table
--    PostgreSQL propagates RENAME COLUMN to all partitions since PG 12.
ALTER TABLE chat.message RENAME COLUMN twitch_id TO channel_id;

-- 4. Update the async tokenization function to use new column name
CREATE OR REPLACE FUNCTION public.fn_tokenize_message_word()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE token TEXT;
BEGIN
    FOR token IN
        SELECT unnest(string_to_array(regexp_replace(lower(NEW.message_text), '[^a-zA-Z\s]', '', 'g'), ' '))
    LOOP
        IF token <> '' AND NOT EXISTS (SELECT 1 FROM chat.stopwords sw WHERE sw.word = token) THEN
            INSERT INTO chat.message_word (message_id, channel_id, session_id, word)
            VALUES (NEW.id, NEW.channel_id, NEW.session_id, token);
        END IF;
    END LOOP;
    RETURN NEW;
END; $$;

-- 5. Add platform discriminator (default 'twitch' for all existing data)
ALTER TABLE chat."user" ADD COLUMN platform VARCHAR(20) NOT NULL DEFAULT 'twitch';
ALTER TABLE chat.session ADD COLUMN platform VARCHAR(20) NOT NULL DEFAULT 'twitch';
ALTER TABLE chat.message ADD COLUMN platform VARCHAR(20) NOT NULL DEFAULT 'twitch';

-- 6. Add platform_id for platforms with non-numeric IDs (YouTube: "UCxxxx")
ALTER TABLE chat."user" ADD COLUMN platform_id VARCHAR(255);
UPDATE chat."user" SET platform_id = id::text;

-- 7. Composite unique constraint: platform + user ID
ALTER TABLE chat."user" ADD CONSTRAINT uq_user_platform_id UNIQUE (platform, id);

-- 8. Update the exclusive active session constraint to be per-platform
ALTER TABLE chat.session DROP CONSTRAINT IF EXISTS unique_active_session;
ALTER TABLE chat.session ADD CONSTRAINT unique_active_session
    EXCLUDE USING btree (channel_id WITH =, platform WITH =) WHERE (end_time IS NULL);

-- 9. Cross-platform streamer identity (manual linking)
CREATE TABLE chat.streamer (
    id             BIGSERIAL PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat.streamer_platform_link (
    id               BIGSERIAL PRIMARY KEY,
    streamer_id      BIGINT NOT NULL REFERENCES chat.streamer(id),
    platform         VARCHAR(20) NOT NULL,
    platform_user_id BIGINT NOT NULL,
    platform_login   VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(platform, platform_user_id)
);

-- 10. Platform-specific indexes
CREATE INDEX idx_message_platform ON chat.message(platform);
CREATE INDEX idx_session_platform ON chat.session(platform);
