ALTER TABLE diocese DROP CONSTRAINT IF EXISTS uk_diocese_code;
ALTER TABLE deanery DROP CONSTRAINT IF EXISTS uk_deanery_diocese_code;
ALTER TABLE parish DROP CONSTRAINT IF EXISTS uk_parish_deanery_code;

ALTER TABLE diocese DROP COLUMN IF EXISTS diocese_code;
ALTER TABLE deanery DROP COLUMN IF EXISTS deanery_code;
ALTER TABLE parish DROP COLUMN IF EXISTS parish_code;
