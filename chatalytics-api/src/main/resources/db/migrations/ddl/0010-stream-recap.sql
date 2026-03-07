-- Persisted stream recaps: computed once when a stream ends, never recalculated
CREATE TABLE twitch.stream_recap
(
    session_id              bigint    PRIMARY KEY REFERENCES twitch.session (id),
    start_time              timestamp NOT NULL,
    end_time                timestamp NOT NULL,
    total_messages          bigint    NOT NULL DEFAULT 0,
    total_chatters          bigint    NOT NULL DEFAULT 0,
    messages_per_minute     double precision NOT NULL DEFAULT 0,
    chatters_per_minute     double precision NOT NULL DEFAULT 0,
    peak_viewer_count       integer,
    avg_viewer_count        double precision,
    min_viewer_count        integer,
    new_chatter_count       bigint    NOT NULL DEFAULT 0,
    returning_chatter_count bigint    NOT NULL DEFAULT 0,
    chat_participation_rate double precision,

    -- Message analysis (flattened)
    avg_message_length      double precision,
    median_message_length   double precision,
    command_count           bigint,
    short_message_ratio     double precision,
    caps_ratio              double precision,
    question_ratio          double precision,
    exclamation_ratio       double precision,
    link_count              bigint,

    -- Peak moment
    peak_moment_timestamp   timestamp,
    peak_moment_messages    bigint,
    peak_moment_chatters    bigint,

    -- AI summary
    ai_summary              text,

    -- Collections stored as JSONB for flexibility
    snapshots               jsonb     NOT NULL DEFAULT '[]',
    chat_activity           jsonb     NOT NULL DEFAULT '[]',
    top_chatters            jsonb     NOT NULL DEFAULT '[]',
    top_words               jsonb     NOT NULL DEFAULT '[]',
    game_segments           jsonb     NOT NULL DEFAULT '[]',
    top_clips               jsonb     NOT NULL DEFAULT '[]',
    hype_moments            jsonb     NOT NULL DEFAULT '[]',

    created_at              timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
