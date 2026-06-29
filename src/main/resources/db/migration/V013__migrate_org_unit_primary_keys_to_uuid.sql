CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE diocese ADD COLUMN id UUID;
UPDATE diocese SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE diocese ALTER COLUMN id SET NOT NULL;

ALTER TABLE deanery ADD COLUMN id UUID;
ALTER TABLE deanery ADD COLUMN diocese_id UUID;
UPDATE deanery de
SET id = gen_random_uuid(),
    diocese_id = d.id
FROM diocese d
WHERE de.diocese_code = d.diocese_code;
ALTER TABLE deanery ALTER COLUMN id SET NOT NULL;
ALTER TABLE deanery ALTER COLUMN diocese_id SET NOT NULL;

ALTER TABLE parish ADD COLUMN id UUID;
ALTER TABLE parish ADD COLUMN deanery_id UUID;
UPDATE parish p
SET id = gen_random_uuid(),
    deanery_id = de.id
FROM deanery de
WHERE p.deanery_code = de.deanery_code;
ALTER TABLE parish ALTER COLUMN id SET NOT NULL;
ALTER TABLE parish ALTER COLUMN deanery_id SET NOT NULL;

ALTER TABLE leader ADD COLUMN parish_id UUID;
UPDATE leader l
SET parish_id = p.id
FROM parish p
WHERE l.parish_code = p.parish_code;
ALTER TABLE leader ALTER COLUMN parish_id SET NOT NULL;

ALTER TABLE deanery DROP CONSTRAINT IF EXISTS fk_deanery_diocese;
ALTER TABLE parish DROP CONSTRAINT IF EXISTS fk_parish_deanery;
ALTER TABLE leader DROP CONSTRAINT IF EXISTS fk_leader_parish;

DROP INDEX IF EXISTS idx_deanery_diocese_code;
DROP INDEX IF EXISTS idx_parish_deanery_code;
DROP INDEX IF EXISTS idx_leader_parish_code;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname
    INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'diocese'::regclass
      AND contype = 'p';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE diocese DROP CONSTRAINT %I CASCADE', constraint_name);
    END IF;
END $$;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname
    INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'deanery'::regclass
      AND contype = 'p';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE deanery DROP CONSTRAINT %I CASCADE', constraint_name);
    END IF;
END $$;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname
    INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'parish'::regclass
      AND contype = 'p';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE parish DROP CONSTRAINT %I CASCADE', constraint_name);
    END IF;
END $$;

ALTER TABLE diocese ADD CONSTRAINT pk_diocese_id PRIMARY KEY (id);
ALTER TABLE deanery ADD CONSTRAINT pk_deanery_id PRIMARY KEY (id);
ALTER TABLE parish ADD CONSTRAINT pk_parish_id PRIMARY KEY (id);

ALTER TABLE deanery
    ADD CONSTRAINT fk_deanery_diocese_id FOREIGN KEY (diocese_id) REFERENCES diocese (id);
ALTER TABLE parish
    ADD CONSTRAINT fk_parish_deanery_id FOREIGN KEY (deanery_id) REFERENCES deanery (id);
ALTER TABLE leader
    ADD CONSTRAINT fk_leader_parish_id FOREIGN KEY (parish_id) REFERENCES parish (id);

ALTER TABLE deanery DROP COLUMN diocese_code;
ALTER TABLE parish DROP COLUMN deanery_code;
ALTER TABLE leader DROP COLUMN parish_code;

CREATE INDEX idx_deanery_diocese_id ON deanery (diocese_id);
CREATE INDEX idx_parish_deanery_id ON parish (deanery_id);
CREATE INDEX idx_leader_parish_id ON leader (parish_id);
