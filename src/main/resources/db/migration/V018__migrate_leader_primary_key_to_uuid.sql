CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE leader
    ADD COLUMN IF NOT EXISTS id UUID;

UPDATE leader
SET id = CASE
             WHEN leader_code ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
                 THEN leader_code::uuid
             ELSE gen_random_uuid()
    END
WHERE id IS NULL;

ALTER TABLE leader
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE account
    ADD COLUMN IF NOT EXISTS leader_id_uuid UUID;

UPDATE account a
SET leader_id_uuid = l.id
FROM leader l
WHERE a.leader_id = l.leader_code
  AND a.leader_id_uuid IS NULL;

ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS leader_id_uuid UUID;

UPDATE certificate c
SET leader_id_uuid = l.id
FROM leader l
WHERE c.leader_id = l.leader_code
  AND c.leader_id_uuid IS NULL;

ALTER TABLE rank_history
    ADD COLUMN IF NOT EXISTS leader_id_uuid UUID;

UPDATE rank_history r
SET leader_id_uuid = l.id
FROM leader l
WHERE r.leader_id = l.leader_code
  AND r.leader_id_uuid IS NULL;

ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS leader_id_uuid UUID;

UPDATE course_participation cp
SET leader_id_uuid = l.id
FROM leader l
WHERE cp.leader_id = l.leader_code
  AND cp.leader_id_uuid IS NULL;

ALTER TABLE executive_board_assignment
    ADD COLUMN IF NOT EXISTS leader_id_uuid UUID;

UPDATE executive_board_assignment e
SET leader_id_uuid = l.id
FROM leader l
WHERE e.leader_id = l.leader_code
  AND e.leader_id_uuid IS NULL;

ALTER TABLE account DROP CONSTRAINT IF EXISTS fk_account_leader_id;
ALTER TABLE certificate DROP CONSTRAINT IF EXISTS fk_certificate_leader_id;
ALTER TABLE rank_history DROP CONSTRAINT IF EXISTS fk_rank_history_leader_id;
ALTER TABLE course_participation DROP CONSTRAINT IF EXISTS fk_course_participation_leader_id;
ALTER TABLE executive_board_assignment DROP CONSTRAINT IF EXISTS fk_executive_board_leader_id;
ALTER TABLE leader DROP CONSTRAINT IF EXISTS uk_leader_code;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname
    INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'leader'::regclass
      AND contype = 'p';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE leader DROP CONSTRAINT %I CASCADE', constraint_name);
    END IF;
END $$;

ALTER TABLE account DROP COLUMN IF EXISTS leader_id;
ALTER TABLE certificate DROP COLUMN IF EXISTS leader_id;
ALTER TABLE rank_history DROP COLUMN IF EXISTS leader_id;
ALTER TABLE course_participation DROP COLUMN IF EXISTS leader_id;
ALTER TABLE executive_board_assignment DROP COLUMN IF EXISTS leader_id;

ALTER TABLE account RENAME COLUMN leader_id_uuid TO leader_id;
ALTER TABLE certificate RENAME COLUMN leader_id_uuid TO leader_id;
ALTER TABLE rank_history RENAME COLUMN leader_id_uuid TO leader_id;
ALTER TABLE course_participation RENAME COLUMN leader_id_uuid TO leader_id;
ALTER TABLE executive_board_assignment RENAME COLUMN leader_id_uuid TO leader_id;

ALTER TABLE leader ADD CONSTRAINT pk_leader_id PRIMARY KEY (id);

ALTER TABLE account
    ADD CONSTRAINT fk_account_leader_id FOREIGN KEY (leader_id) REFERENCES leader (id) ON DELETE SET NULL;
ALTER TABLE certificate
    ADD CONSTRAINT fk_certificate_leader_id FOREIGN KEY (leader_id) REFERENCES leader (id) ON DELETE SET NULL;
ALTER TABLE rank_history
    ADD CONSTRAINT fk_rank_history_leader_id FOREIGN KEY (leader_id) REFERENCES leader (id) ON DELETE SET NULL;
ALTER TABLE course_participation
    ADD CONSTRAINT fk_course_participation_leader_id FOREIGN KEY (leader_id) REFERENCES leader (id) ON DELETE SET NULL;
ALTER TABLE executive_board_assignment
    ADD CONSTRAINT fk_executive_board_leader_id FOREIGN KEY (leader_id) REFERENCES leader (id) ON DELETE SET NULL;

DROP INDEX IF EXISTS idx_account_leader_id;
DROP INDEX IF EXISTS idx_certificate_leader_id;
DROP INDEX IF EXISTS idx_rank_history_leader_id;
DROP INDEX IF EXISTS idx_course_participation_leader_id;
DROP INDEX IF EXISTS idx_executive_board_leader_id;

CREATE INDEX IF NOT EXISTS idx_account_leader_id ON account (leader_id);
CREATE INDEX IF NOT EXISTS idx_certificate_leader_id ON certificate (leader_id);
CREATE INDEX IF NOT EXISTS idx_rank_history_leader_id ON rank_history (leader_id);
CREATE INDEX IF NOT EXISTS idx_course_participation_leader_id ON course_participation (leader_id);
CREATE INDEX IF NOT EXISTS idx_executive_board_leader_id ON executive_board_assignment (leader_id);

ALTER TABLE leader DROP COLUMN IF EXISTS leader_code;
