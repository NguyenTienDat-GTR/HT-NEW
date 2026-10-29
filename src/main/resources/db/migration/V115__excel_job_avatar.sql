ALTER TABLE excel_job
    ADD COLUMN IF NOT EXISTS images_path VARCHAR(500),
    ADD COLUMN IF NOT EXISTS avatar_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS avatar_plan JSONB,
    ADD COLUMN IF NOT EXISTS avatar_lease_until TIMESTAMPTZ;

ALTER TABLE excel_job DROP CONSTRAINT IF EXISTS chk_excel_job_avatar_status;
ALTER TABLE excel_job ADD CONSTRAINT chk_excel_job_avatar_status
    CHECK (avatar_status IS NULL OR avatar_status IN ('PENDING', 'RUNNING', 'DONE'));

CREATE INDEX IF NOT EXISTS idx_excel_job_avatar_status
    ON excel_job (avatar_status, avatar_lease_until)
    WHERE avatar_status IN ('PENDING', 'RUNNING');
