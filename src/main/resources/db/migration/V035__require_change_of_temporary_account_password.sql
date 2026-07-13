ALTER TABLE account
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN account.must_change_password IS
    'True when the account was issued a temporary credential and must change it before using business APIs.';
