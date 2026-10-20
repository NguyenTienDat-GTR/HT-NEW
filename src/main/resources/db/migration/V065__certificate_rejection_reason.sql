ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
