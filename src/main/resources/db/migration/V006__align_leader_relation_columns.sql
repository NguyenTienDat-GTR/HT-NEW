-- Align legacy *_code relation columns with current JPA @JoinColumn mappings.

-- certificate.leader_code -> certificate.leader_id
ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS leader_id VARCHAR(20);

UPDATE certificate
SET leader_id = leader_code
WHERE leader_id IS NULL
  AND leader_code IS NOT NULL;

ALTER TABLE certificate
    DROP CONSTRAINT IF EXISTS fk_certificate_leader;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_certificate_leader_id'
    ) THEN
        EXECUTE 'ALTER TABLE certificate ADD CONSTRAINT fk_certificate_leader_id FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE SET NULL';
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_certificate_leader_id ON certificate(leader_id);
DROP INDEX IF EXISTS idx_certificate_leader_code;

ALTER TABLE certificate
    DROP COLUMN IF EXISTS leader_code;

-- rank_history.leader_code -> rank_history.leader_id
ALTER TABLE rank_history
    ADD COLUMN IF NOT EXISTS leader_id VARCHAR(20);

UPDATE rank_history
SET leader_id = leader_code
WHERE leader_id IS NULL
  AND leader_code IS NOT NULL;

ALTER TABLE rank_history
    DROP CONSTRAINT IF EXISTS fk_rank_history_leader;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_rank_history_leader_id'
    ) THEN
        EXECUTE 'ALTER TABLE rank_history ADD CONSTRAINT fk_rank_history_leader_id FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE SET NULL';
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_rank_history_leader_id ON rank_history(leader_id);
DROP INDEX IF EXISTS idx_rank_history_leader_code;

ALTER TABLE rank_history
    DROP COLUMN IF EXISTS leader_code;

-- course_participation.leader_code/course_code -> leader_id/course_id
ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS leader_id VARCHAR(20);

UPDATE course_participation
SET leader_id = leader_code
WHERE leader_id IS NULL
  AND leader_code IS NOT NULL;

ALTER TABLE course_participation
    DROP CONSTRAINT IF EXISTS fk_course_participation_leader;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_course_participation_leader_id'
    ) THEN
        EXECUTE 'ALTER TABLE course_participation ADD CONSTRAINT fk_course_participation_leader_id FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE SET NULL';
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_course_participation_leader_id ON course_participation(leader_id);
DROP INDEX IF EXISTS idx_course_participation_leader_code;

ALTER TABLE course_participation
    DROP COLUMN IF EXISTS leader_code;

ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS course_id VARCHAR(255);

UPDATE course_participation
SET course_id = course_code
WHERE course_id IS NULL
  AND course_code IS NOT NULL;

ALTER TABLE course_participation
    DROP CONSTRAINT IF EXISTS fk_course_participation_course;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_course_participation_course_id'
    ) THEN
        EXECUTE 'ALTER TABLE course_participation ADD CONSTRAINT fk_course_participation_course_id FOREIGN KEY (course_id) REFERENCES training_course(course_code) ON DELETE SET NULL';
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_course_participation_course_id ON course_participation(course_id);
DROP INDEX IF EXISTS idx_course_participation_course_code;

ALTER TABLE course_participation
    DROP COLUMN IF EXISTS course_code;

-- executive_board_assignment.leader_code/position_code -> leader_id/position_id
ALTER TABLE executive_board_assignment
    ADD COLUMN IF NOT EXISTS leader_id VARCHAR(20);

UPDATE executive_board_assignment
SET leader_id = leader_code
WHERE leader_id IS NULL
  AND leader_code IS NOT NULL;

ALTER TABLE executive_board_assignment
    DROP CONSTRAINT IF EXISTS fk_executive_board_leader;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_executive_board_leader_id'
    ) THEN
        EXECUTE 'ALTER TABLE executive_board_assignment ADD CONSTRAINT fk_executive_board_leader_id FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE SET NULL';
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_executive_board_leader_id ON executive_board_assignment(leader_id);
DROP INDEX IF EXISTS idx_executive_board_leader_code;

ALTER TABLE executive_board_assignment
    DROP COLUMN IF EXISTS leader_code;

ALTER TABLE executive_board_assignment
    ADD COLUMN IF NOT EXISTS position_id VARCHAR(255);

UPDATE executive_board_assignment
SET position_id = position_code
WHERE position_id IS NULL
  AND position_code IS NOT NULL;

ALTER TABLE executive_board_assignment
    DROP CONSTRAINT IF EXISTS fk_executive_board_position;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_executive_board_position_id'
    ) THEN
        EXECUTE 'ALTER TABLE executive_board_assignment ADD CONSTRAINT fk_executive_board_position_id FOREIGN KEY (position_id) REFERENCES position(position_code) ON DELETE SET NULL';
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_executive_board_position_id ON executive_board_assignment(position_id);
DROP INDEX IF EXISTS idx_executive_board_position_code;

ALTER TABLE executive_board_assignment
    DROP COLUMN IF EXISTS position_code;
