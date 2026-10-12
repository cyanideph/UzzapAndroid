-- UzzapBot tuning units: activity.cooldown_seconds was stored as 86400000
-- (milliseconds) in a seconds column — 1000 days of silence if ever read
-- as seconds. The bot now reads this table live (60s cache) with hardcoded
-- fallbacks, so the stored values must be in the documented units (seconds).
UPDATE public.uzzapbot_game_config
SET config_value = '86400',
    description = COALESCE(NULLIF(description, ''), 'Inactivity cooldown before the bot posts, in seconds.'),
    updated_at = now()
WHERE config_key = 'activity.cooldown_seconds';
