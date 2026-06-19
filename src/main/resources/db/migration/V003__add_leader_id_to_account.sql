-- =============================================================================
-- V003: Add leader_id column to account, backfill and remove legacy leader_code
-- Purpose: Ensure JPA mapping uses `leader_id` (JoinColumn(name = 'leader_id'))
-- and remove legacy `leader_code` column safely.
-- IMPORTANT: Constraint creation uses a guarded DO block (no ALTER ... IF NOT EXISTS)
-- =============================================================================

-- 1) Add new column leader_id if not present (non-destructive)
ALTER TABLE account
    ADD COLUMN IF NOT EXISTS leader_id VARCHAR(20);

-- 2) Backfill leader_id from existing leader_code values
UPDATE account
SET leader_id = leader_code
WHERE leader_id IS NULL
  AND leader_code IS NOT NULL;

-- 3) Add foreign key constraint for leader_id if not already created
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_account_leader_id'
    ) THEN
        EXECUTE 'ALTER TABLE account ADD CONSTRAINT fk_account_leader_id FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE SET NULL';
    END IF;
END$$;

-- 4) Create index for leader_id to speed joins
CREATE INDEX IF NOT EXISTS idx_account_leader_id ON account(leader_id);

-- 5) Remove legacy foreign key constraint that references leader_code (if present)
ALTER TABLE account
    DROP CONSTRAINT IF EXISTS fk_account_leader;

-- 6) Drop legacy index on leader_code if it exists (common name used previously)
DROP INDEX IF EXISTS idx_account_leader_code;

-- 7) Finally drop legacy column leader_code
ALTER TABLE account
    DROP COLUMN IF EXISTS leader_code;

-- =============================================================================
-- End V003
-- =============================================================================

