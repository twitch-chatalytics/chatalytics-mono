-- Step 7: SocialBlade hybrid BYOK infrastructure

-- Add featured flag to user table (server-side SocialBlade refresh only for featured channels)
ALTER TABLE twitch.user ADD COLUMN IF NOT EXISTS featured BOOLEAN DEFAULT FALSE;

-- Viewer API key storage for BYOK providers (SocialBlade, etc.)
CREATE TABLE IF NOT EXISTS twitch.viewer_api_key
(
    id               bigserial PRIMARY KEY,
    viewer_twitch_id bigint      NOT NULL,
    provider         varchar(50) NOT NULL,
    encrypted_key    TEXT        NOT NULL,
    encrypted_secret TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (viewer_twitch_id, provider)
);

CREATE INDEX idx_viewer_api_key_viewer ON twitch.viewer_api_key (viewer_twitch_id);
CREATE INDEX idx_viewer_api_key_provider ON twitch.viewer_api_key (provider);
