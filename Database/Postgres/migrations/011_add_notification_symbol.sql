-- Store the instrument symbol beside each in-app notification so the
-- header panel can show it without parsing the message text.

ALTER TABLE tradingsystem.notification
    ADD COLUMN IF NOT EXISTS symbol VARCHAR(20);
