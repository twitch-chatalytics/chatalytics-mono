-- Alert rules configured by users
CREATE TABLE IF NOT EXISTS twitch.alert_rule (
    id BIGSERIAL PRIMARY KEY,
    twitch_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    threshold_value DOUBLE PRECISION,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Alert events that have been triggered
CREATE TABLE IF NOT EXISTS twitch.alert_event (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT REFERENCES twitch.alert_rule(id),
    twitch_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'info',
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alert_event_twitch_id ON twitch.alert_event(twitch_id);
CREATE INDEX IF NOT EXISTS idx_alert_rule_twitch_id ON twitch.alert_rule(twitch_id);
