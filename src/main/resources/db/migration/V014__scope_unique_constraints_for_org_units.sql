DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'diocese'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'diocese'::regclass AND attname = 'diocese_code')
          ]
    LOOP
        EXECUTE format('ALTER TABLE diocese DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

WITH duplicate_diocese_names AS (
    SELECT id,
           name,
           ROW_NUMBER() OVER (PARTITION BY name ORDER BY created_at NULLS LAST, id) AS rn
    FROM diocese
)
UPDATE diocese d
SET name = LEFT(duplicate_diocese_names.name || ' (' || duplicate_diocese_names.rn || ')', 100)
FROM duplicate_diocese_names
WHERE d.id = duplicate_diocese_names.id
  AND duplicate_diocese_names.rn > 1;

ALTER TABLE diocese DROP CONSTRAINT IF EXISTS uk_diocese_name;
ALTER TABLE diocese DROP CONSTRAINT IF EXISTS uk_diocese_code;
ALTER TABLE diocese ADD CONSTRAINT uk_diocese_name UNIQUE (name);
ALTER TABLE diocese ADD CONSTRAINT uk_diocese_code UNIQUE (diocese_code);

ALTER TABLE deanery DROP CONSTRAINT IF EXISTS uk_deanery_diocese_name;
ALTER TABLE deanery DROP CONSTRAINT IF EXISTS uk_deanery_diocese_code;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'deanery'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'deanery'::regclass AND attname = 'deanery_code')
          ]
    LOOP
        EXECUTE format('ALTER TABLE deanery DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

WITH duplicate_deanery_names AS (
    SELECT id,
           name,
           ROW_NUMBER() OVER (PARTITION BY diocese_id, name ORDER BY created_at NULLS LAST, id) AS rn
    FROM deanery
)
UPDATE deanery de
SET name = LEFT(duplicate_deanery_names.name || ' (' || duplicate_deanery_names.rn || ')', 100)
FROM duplicate_deanery_names
WHERE de.id = duplicate_deanery_names.id
  AND duplicate_deanery_names.rn > 1;

WITH duplicate_deanery_codes AS (
    SELECT id,
           deanery_code,
           ROW_NUMBER() OVER (PARTITION BY diocese_id, deanery_code ORDER BY created_at NULLS LAST, id) AS rn
    FROM deanery
)
UPDATE deanery de
SET deanery_code = LEFT(
        duplicate_deanery_codes.deanery_code,
        50 - LENGTH('-' || duplicate_deanery_codes.rn)
    ) || '-' || duplicate_deanery_codes.rn
FROM duplicate_deanery_codes
WHERE de.id = duplicate_deanery_codes.id
  AND duplicate_deanery_codes.rn > 1;

ALTER TABLE deanery ADD CONSTRAINT uk_deanery_diocese_name UNIQUE (diocese_id, name);
ALTER TABLE deanery ADD CONSTRAINT uk_deanery_diocese_code UNIQUE (diocese_id, deanery_code);

ALTER TABLE parish DROP CONSTRAINT IF EXISTS uk_parish_deanery_name;
ALTER TABLE parish DROP CONSTRAINT IF EXISTS uk_parish_deanery_code;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'parish'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = 'parish'::regclass AND attname = 'parish_code')
          ]
    LOOP
        EXECUTE format('ALTER TABLE parish DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

WITH duplicate_parish_names AS (
    SELECT id,
           name,
           ROW_NUMBER() OVER (PARTITION BY deanery_id, name ORDER BY created_at NULLS LAST, id) AS rn
    FROM parish
)
UPDATE parish p
SET name = LEFT(duplicate_parish_names.name || ' (' || duplicate_parish_names.rn || ')', 100)
FROM duplicate_parish_names
WHERE p.id = duplicate_parish_names.id
  AND duplicate_parish_names.rn > 1;

WITH duplicate_parish_codes AS (
    SELECT id,
           parish_code,
           ROW_NUMBER() OVER (PARTITION BY deanery_id, parish_code ORDER BY created_at NULLS LAST, id) AS rn
    FROM parish
)
UPDATE parish p
SET parish_code = LEFT(
        duplicate_parish_codes.parish_code,
        50 - LENGTH('-' || duplicate_parish_codes.rn)
    ) || '-' || duplicate_parish_codes.rn
FROM duplicate_parish_codes
WHERE p.id = duplicate_parish_codes.id
  AND duplicate_parish_codes.rn > 1;

ALTER TABLE parish ADD CONSTRAINT uk_parish_deanery_name UNIQUE (deanery_id, name);
ALTER TABLE parish ADD CONSTRAINT uk_parish_deanery_code UNIQUE (deanery_id, parish_code);
