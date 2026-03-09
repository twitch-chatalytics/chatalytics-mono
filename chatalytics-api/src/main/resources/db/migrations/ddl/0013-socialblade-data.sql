-- SocialBlade channel snapshot (current metrics)
CREATE TABLE IF NOT EXISTS twitch.socialblade_channel (
    twitch_id       BIGINT PRIMARY KEY,
    username        VARCHAR(100),
    display_name    VARCHAR(100),
    followers       BIGINT,
    views           BIGINT,
    grade           VARCHAR(10),
    rank            INTEGER,
    follower_rank   INTEGER,

    -- Gains (last 30 / 90 / 180 days)
    followers_gained_30d  INTEGER,
    followers_gained_90d  INTEGER,
    followers_gained_180d INTEGER,
    views_gained_30d      BIGINT,
    views_gained_90d      BIGINT,
    views_gained_180d     BIGINT,

    -- Social links (from YouTube lookup)
    youtube_url     VARCHAR(500),
    twitter_url     VARCHAR(500),
    instagram_url   VARCHAR(500),
    discord_url     VARCHAR(500),
    tiktok_url      VARCHAR(500),

    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SocialBlade daily historical data
CREATE TABLE IF NOT EXISTS twitch.socialblade_daily (
    twitch_id       BIGINT NOT NULL,
    date            DATE NOT NULL,
    followers       BIGINT,
    views           BIGINT,
    follower_change INTEGER,
    view_change     BIGINT,
    PRIMARY KEY (twitch_id, date)
);

CREATE INDEX IF NOT EXISTS idx_socialblade_daily_twitch_id
    ON twitch.socialblade_daily (twitch_id);
