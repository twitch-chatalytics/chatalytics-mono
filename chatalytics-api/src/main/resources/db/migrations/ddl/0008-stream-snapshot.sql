-- Stream snapshot table: captures Twitch stream metadata every poll interval
CREATE TABLE twitch.stream_snapshot
(
    id           bigserial PRIMARY KEY,
    session_id   bigint    NOT NULL,
    twitch_id    bigint    NOT NULL,
    timestamp    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    game_name    text,
    title        text,
    viewer_count integer   NOT NULL DEFAULT 0,
    FOREIGN KEY (session_id) REFERENCES twitch.session (id),
    FOREIGN KEY (twitch_id) REFERENCES twitch.user (id)
);

CREATE INDEX idx_stream_snapshot_session ON twitch.stream_snapshot (session_id, timestamp);
CREATE INDEX idx_stream_snapshot_twitch ON twitch.stream_snapshot (twitch_id, timestamp);
