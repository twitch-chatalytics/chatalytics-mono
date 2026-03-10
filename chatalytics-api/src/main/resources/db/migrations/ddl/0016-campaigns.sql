CREATE TABLE IF NOT EXISTS twitch.campaign (
    id BIGSERIAL PRIMARY KEY,
    twitch_id BIGINT NOT NULL,
    campaign_name VARCHAR(255) NOT NULL,
    brand_name VARCHAR(255),
    brand_keywords TEXT[],
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    deal_price DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS twitch.campaign_session (
    campaign_id BIGINT NOT NULL REFERENCES twitch.campaign(id) ON DELETE CASCADE,
    session_id BIGINT NOT NULL,
    PRIMARY KEY (campaign_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_campaign_twitch_id ON twitch.campaign(twitch_id);
