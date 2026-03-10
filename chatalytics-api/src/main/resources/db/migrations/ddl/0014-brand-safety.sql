CREATE TABLE IF NOT EXISTS twitch.channel_brand_safety (
    twitch_id BIGINT PRIMARY KEY,
    brand_safety_score INT NOT NULL,
    toxicity_rate DOUBLE PRECISION,
    positive_rate DOUBLE PRECISION,
    negative_rate DOUBLE PRECISION,
    neutral_rate DOUBLE PRECISION,
    emote_spam_rate DOUBLE PRECISION,
    conversation_ratio DOUBLE PRECISION,
    top_topics JSONB DEFAULT '[]',
    language_distribution JSONB DEFAULT '{}',
    sessions_analyzed INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
