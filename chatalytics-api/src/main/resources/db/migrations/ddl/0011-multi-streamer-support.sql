-- Viewers: authenticated Twitch users who browse the site
CREATE TABLE twitch.viewer
(
    twitch_id         BIGINT PRIMARY KEY,
    login             VARCHAR(255) NOT NULL,
    display_name      VARCHAR(255),
    profile_image_url TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Streamer tracking requests (votes)
CREATE TABLE twitch.streamer_request
(
    id                BIGSERIAL PRIMARY KEY,
    streamer_login    VARCHAR(255) NOT NULL,
    streamer_id       BIGINT,
    display_name      VARCHAR(255),
    profile_image_url TEXT,
    requested_by      BIGINT NOT NULL REFERENCES twitch.viewer(twitch_id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(streamer_login, requested_by)
);

CREATE INDEX idx_streamer_request_login ON twitch.streamer_request(streamer_login);
CREATE INDEX idx_streamer_request_by ON twitch.streamer_request(requested_by);
