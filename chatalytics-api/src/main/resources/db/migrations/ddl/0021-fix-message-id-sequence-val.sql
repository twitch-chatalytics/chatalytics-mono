-- Set sequence value to continue from max existing id (separate transaction to avoid lock conflict)
SELECT setval('twitch.message_id_seq', COALESCE((SELECT MAX(id) FROM twitch.message), 1));
