-- Create schema
CREATE SCHEMA IF NOT EXISTS twitch;

-- Initial user table
CREATE TABLE twitch.user
(
    id                bigint PRIMARY KEY,
    login             varchar NOT NULL,
    display_name      varchar NOT NULL,
    type              varchar,
    broadcaster_type  varchar,
    description       varchar,
    profile_image_url varchar,
    offline_image_url varchar,
    view_count        integer,
    created_at        timestamp DEFAULT CURRENT_TIMESTAMP
);

-- Session tracking
CREATE TABLE twitch.session
(
    id         bigserial PRIMARY KEY,
    twitch_id  bigint NOT NULL,
    start_time timestamp DEFAULT CURRENT_TIMESTAMP,
    end_time   timestamp,
    FOREIGN KEY (twitch_id) REFERENCES twitch.user (id)
);

-- Message storage
CREATE TABLE twitch.message
(
    id           bigserial PRIMARY KEY,
    twitch_id    bigint NOT NULL,
    message_text TEXT   NOT NULL,
    timestamp    TIMESTAMP,
    session_id   bigint NOT NULL,
    author       TEXT   NOT NULL,
    FOREIGN KEY (twitch_id) REFERENCES twitch.user (id),
    FOREIGN KEY (session_id) REFERENCES twitch.session (id)
);

-- Session summaries
CREATE TABLE twitch.session_summary
(
    id                  bigserial PRIMARY KEY,
    twitch_id           bigint NOT NULL,
    session_id          bigint NOT NULL,
    messages_per_minute bigint,
    total_chatters      bigint,
    total_messages      bigint,
    total_sessions      bigint,
    mentions            bigint,
    FOREIGN KEY (session_id) REFERENCES twitch.session (id),
    FOREIGN KEY (twitch_id) REFERENCES twitch.user (id)
);

-- Rollup tracking
CREATE TABLE twitch.rollup_history
(
    id         bigserial PRIMARY KEY,
    session_id bigint NOT NULL,
    complete   boolean,
    FOREIGN KEY (session_id) REFERENCES twitch.session (id)
);

-- Word tokenization
CREATE TABLE twitch.stopwords
(
    word TEXT PRIMARY KEY
);

CREATE TABLE twitch.message_word
(
    id         bigserial PRIMARY KEY,
    message_id bigint NOT NULL,
    twitch_id  bigint NOT NULL,
    session_id bigint NOT NULL,
    word       TEXT   NOT NULL
);

-- Indexes
CREATE INDEX idx_message_author ON twitch.message (author);
CREATE INDEX idx_message_twitch_id ON twitch.message (twitch_id);
CREATE INDEX idx_session_twitch_session_id ON twitch.session (twitch_id, id);
CREATE INDEX idx_message_word_twitch_session ON twitch.message_word (twitch_id, session_id);
CREATE INDEX idx_message_word_word ON twitch.message_word (word);
CREATE INDEX idx_message_processed_text ON twitch.message (lower(regexp_replace(message_text, '[^a-zA-Z\s]', '', 'g')));

-- Constraints
ALTER TABLE twitch.session
    ADD CONSTRAINT unique_active_session EXCLUDE USING btree (twitch_id WITH =)
        WHERE (end_time IS NULL);

-- Tokenization function
CREATE
OR REPLACE FUNCTION public.fn_tokenize_message_word()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
token TEXT;
BEGIN
FOR token IN
SELECT unnest(string_to_array(regexp_replace(lower(NEW.message_text), '[^a-zA-Z\s]', '', 'g'), ' ')) AS token
    LOOP
        IF token <> '' AND NOT EXISTS (SELECT 1 FROM twitch.stopwords sw WHERE sw.word = token) THEN
INSERT
INTO twitch.message_word (message_id, twitch_id, session_id, word)
VALUES (NEW.id, NEW.twitch_id, NEW.session_id, token);
END IF;
END LOOP;
RETURN NEW;
END;
$$;

-- Trigger
CREATE TRIGGER tokenize_message_text
    AFTER INSERT
    ON twitch.message
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_tokenize_message_word();

-- Configure work_mem
ALTER
DATABASE postgres SET work_mem = '512MB';