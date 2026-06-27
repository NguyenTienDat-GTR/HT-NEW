CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE leader
    DROP CONSTRAINT IF EXISTS fk_leader_parish;

ALTER TABLE parish
    DROP CONSTRAINT IF EXISTS fk_parish_deanery;

ALTER TABLE deanery
    DROP CONSTRAINT IF EXISTS fk_deanery_diocese;

ALTER TABLE diocese
    ALTER COLUMN diocese_code TYPE VARCHAR(50);

ALTER TABLE deanery
    ALTER COLUMN deanery_code TYPE VARCHAR(50),
    ALTER COLUMN diocese_code TYPE VARCHAR(50);

ALTER TABLE parish
    ALTER COLUMN parish_code TYPE VARCHAR(50),
    ALTER COLUMN deanery_code TYPE VARCHAR(50);

ALTER TABLE leader
    ALTER COLUMN parish_code TYPE VARCHAR(50);

CREATE OR REPLACE FUNCTION slugify_organization_unit(value TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT regexp_replace(
        regexp_replace(
            lower(unaccent(coalesce(value, ''))),
            '[^a-z0-9]+',
            '-',
            'g'
        ),
        '(^-+|-+$)',
        '',
        'g'
    );
$$;

CREATE OR REPLACE FUNCTION trim_slug_for_code(value TEXT, max_length INTEGER)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    trimmed TEXT;
BEGIN
    IF value IS NULL THEN
        RETURN NULL;
    END IF;

    IF length(value) <= max_length THEN
        RETURN value;
    END IF;

    trimmed := regexp_replace(left(value, max_length), '-+$', '');
    IF trimmed = '' THEN
        RETURN left(value, max_length);
    END IF;

    RETURN trimmed;
END;
$$;

DO $$
DECLARE
    diocese_row RECORD;
    deanery_row RECORD;
    parish_row RECORD;
    base_code TEXT;
    candidate_code TEXT;
    suffix_index INTEGER;
    suffix_text TEXT;
BEGIN
    CREATE TEMP TABLE tmp_diocese_code_map (
        old_code VARCHAR(50) PRIMARY KEY,
        new_code VARCHAR(50) NOT NULL UNIQUE
    ) ON COMMIT DROP;

    CREATE TEMP TABLE tmp_deanery_code_map (
        old_code VARCHAR(50) PRIMARY KEY,
        new_code VARCHAR(50) NOT NULL UNIQUE
    ) ON COMMIT DROP;

    CREATE TEMP TABLE tmp_parish_code_map (
        old_code VARCHAR(50) PRIMARY KEY,
        new_code VARCHAR(50) NOT NULL UNIQUE
    ) ON COMMIT DROP;

    FOR diocese_row IN
        SELECT diocese_code, union_name, created_at
        FROM diocese
        ORDER BY created_at NULLS FIRST, union_name, diocese_code
    LOOP
        base_code := slugify_organization_unit(diocese_row.union_name);
        IF base_code = '' THEN
            base_code := slugify_organization_unit(diocese_row.diocese_code);
        END IF;

        candidate_code := trim_slug_for_code(base_code, 50);
        suffix_index := 2;

        WHILE EXISTS (
            SELECT 1
            FROM tmp_diocese_code_map
            WHERE new_code = candidate_code
        ) LOOP
            suffix_text := '-' || suffix_index;
            candidate_code := trim_slug_for_code(base_code, 50 - length(suffix_text)) || suffix_text;
            suffix_index := suffix_index + 1;
        END LOOP;

        INSERT INTO tmp_diocese_code_map (old_code, new_code)
        VALUES (diocese_row.diocese_code, candidate_code);
    END LOOP;

    FOR deanery_row IN
        SELECT d.deanery_code,
               d.association_name,
               d.created_at,
               dm.new_code AS parent_diocese_code
        FROM deanery d
        JOIN tmp_diocese_code_map dm
          ON dm.old_code = d.diocese_code
        ORDER BY d.created_at NULLS FIRST, d.association_name, d.deanery_code
    LOOP
        base_code := slugify_organization_unit(
            deanery_row.parent_diocese_code || '-' || deanery_row.association_name
        );
        IF base_code = '' THEN
            base_code := slugify_organization_unit(deanery_row.deanery_code);
        END IF;

        candidate_code := trim_slug_for_code(base_code, 50);
        suffix_index := 2;

        WHILE EXISTS (
            SELECT 1
            FROM tmp_deanery_code_map
            WHERE new_code = candidate_code
        ) LOOP
            suffix_text := '-' || suffix_index;
            candidate_code := trim_slug_for_code(base_code, 50 - length(suffix_text)) || suffix_text;
            suffix_index := suffix_index + 1;
        END LOOP;

        INSERT INTO tmp_deanery_code_map (old_code, new_code)
        VALUES (deanery_row.deanery_code, candidate_code);
    END LOOP;

    FOR parish_row IN
        SELECT p.parish_code,
               p.chapter_name,
               p.created_at,
               dm.new_code AS parent_deanery_code
        FROM parish p
        JOIN tmp_deanery_code_map dm
          ON dm.old_code = p.deanery_code
        ORDER BY p.created_at NULLS FIRST, p.chapter_name, p.parish_code
    LOOP
        base_code := slugify_organization_unit(
            parish_row.parent_deanery_code || '-' || parish_row.chapter_name
        );
        IF base_code = '' THEN
            base_code := slugify_organization_unit(parish_row.parish_code);
        END IF;

        candidate_code := trim_slug_for_code(base_code, 50);
        suffix_index := 2;

        WHILE EXISTS (
            SELECT 1
            FROM tmp_parish_code_map
            WHERE new_code = candidate_code
        ) LOOP
            suffix_text := '-' || suffix_index;
            candidate_code := trim_slug_for_code(base_code, 50 - length(suffix_text)) || suffix_text;
            suffix_index := suffix_index + 1;
        END LOOP;

        INSERT INTO tmp_parish_code_map (old_code, new_code)
        VALUES (parish_row.parish_code, candidate_code);
    END LOOP;

    UPDATE deanery d
    SET diocese_code = m.new_code
    FROM tmp_diocese_code_map m
    WHERE d.diocese_code = m.old_code;

    UPDATE diocese d
    SET diocese_code = m.new_code
    FROM tmp_diocese_code_map m
    WHERE d.diocese_code = m.old_code;

    UPDATE parish p
    SET deanery_code = m.new_code
    FROM tmp_deanery_code_map m
    WHERE p.deanery_code = m.old_code;

    UPDATE deanery d
    SET deanery_code = m.new_code
    FROM tmp_deanery_code_map m
    WHERE d.deanery_code = m.old_code;

    UPDATE leader l
    SET parish_code = m.new_code
    FROM tmp_parish_code_map m
    WHERE l.parish_code = m.old_code;

    UPDATE parish p
    SET parish_code = m.new_code
    FROM tmp_parish_code_map m
    WHERE p.parish_code = m.old_code;
END
$$;

ALTER TABLE deanery
    ADD CONSTRAINT fk_deanery_diocese
        FOREIGN KEY (diocese_code) REFERENCES diocese (diocese_code);

ALTER TABLE parish
    ADD CONSTRAINT fk_parish_deanery
        FOREIGN KEY (deanery_code) REFERENCES deanery (deanery_code);

ALTER TABLE leader
    ADD CONSTRAINT fk_leader_parish
        FOREIGN KEY (parish_code) REFERENCES parish (parish_code);

DROP FUNCTION IF EXISTS trim_slug_for_code(TEXT, INTEGER);
DROP FUNCTION IF EXISTS slugify_organization_unit(TEXT);
