-- Advertiser accounts (linked to Twitch OAuth viewers)
CREATE TABLE IF NOT EXISTS twitch.advertiser_account (
    id          BIGSERIAL PRIMARY KEY,
    viewer_id   BIGINT UNIQUE NOT NULL REFERENCES twitch.viewer(twitch_id),
    tier        VARCHAR(20) NOT NULL DEFAULT 'trial',
    status      VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-session bot detection / authenticity scores
CREATE TABLE IF NOT EXISTS twitch.session_authenticity (
    session_id                   BIGINT PRIMARY KEY REFERENCES twitch.session(id),
    twitch_id                    BIGINT NOT NULL REFERENCES twitch."user"(id),
    authenticity_score           SMALLINT NOT NULL,
    confidence_level             VARCHAR(10) NOT NULL,

    -- Signal: Chat-to-Viewer Ratio
    chat_viewer_ratio            DOUBLE PRECISION,
    expected_chat_ratio          DOUBLE PRECISION,
    chat_ratio_deviation         DOUBLE PRECISION,

    -- Signal: Message Quality
    vocabulary_diversity         DOUBLE PRECISION,
    emote_only_ratio             DOUBLE PRECISION,
    repetitive_message_ratio     DOUBLE PRECISION,

    -- Signal: Chatter Behavior
    single_message_chatter_ratio DOUBLE PRECISION,
    timing_uniformity_score      DOUBLE PRECISION,

    -- Signal: Engagement Authenticity
    organic_flow_score           DOUBLE PRECISION,
    conversation_depth_score     DOUBLE PRECISION,

    -- Signal: Cross-Session Consistency
    viewer_chat_correlation      DOUBLE PRECISION,

    suspicious_pattern_flags     JSONB DEFAULT '[]'::jsonb,
    algorithm_version            SMALLINT NOT NULL DEFAULT 1,
    computed_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_authenticity_twitch_id
    ON twitch.session_authenticity(twitch_id);

-- Channel-level authenticity rollup
CREATE TABLE IF NOT EXISTS twitch.channel_authenticity (
    twitch_id              BIGINT PRIMARY KEY REFERENCES twitch."user"(id),
    avg_authenticity_score  DOUBLE PRECISION,
    min_authenticity_score  SMALLINT,
    max_authenticity_score  SMALLINT,
    trend_direction        VARCHAR(10) DEFAULT 'stable',
    sessions_analyzed      INT NOT NULL DEFAULT 0,
    risk_level             VARCHAR(10) DEFAULT 'low',
    risk_factors           JSONB DEFAULT '[]'::jsonb,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
