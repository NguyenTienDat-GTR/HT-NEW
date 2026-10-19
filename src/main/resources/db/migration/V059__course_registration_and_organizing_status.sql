-- ============================================================================
-- V059: Trạng thái đăng ký (NOT_STARTED/OPEN/CLOSED/ENDED) và trạng thái tổ chức
--       (UPCOMING/ONGOING/ENDED) lưu trong training_course để lọc và tự chuyển theo thời gian.
--       Thay cột registration_open (V058) bằng registration_status.
-- ============================================================================

ALTER TABLE training_course ADD COLUMN IF NOT EXISTS registration_status VARCHAR(20);
ALTER TABLE training_course ADD COLUMN IF NOT EXISTS organizing_status VARCHAR(20);

UPDATE training_course
SET registration_status = CASE
        WHEN registration_end_at IS NOT NULL AND registration_end_at < CURRENT_TIMESTAMP THEN 'ENDED'
        WHEN registration_open = FALSE THEN 'CLOSED'
        WHEN registration_start_at IS NULL OR registration_end_at IS NULL OR registration_start_at > CURRENT_TIMESTAMP THEN 'NOT_STARTED'
        ELSE 'OPEN'
    END,
    organizing_status = CASE
        WHEN end_date IS NOT NULL AND end_date < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 'ENDED'
        WHEN start_date IS NULL OR start_date > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 'UPCOMING'
        ELSE 'ONGOING'
    END;

ALTER TABLE training_course ALTER COLUMN registration_status SET NOT NULL;
ALTER TABLE training_course ALTER COLUMN registration_status SET DEFAULT 'NOT_STARTED';
ALTER TABLE training_course ALTER COLUMN organizing_status SET NOT NULL;
ALTER TABLE training_course ALTER COLUMN organizing_status SET DEFAULT 'UPCOMING';

ALTER TABLE training_course DROP COLUMN IF EXISTS registration_open;

CREATE INDEX IF NOT EXISTS idx_training_course_registration_status ON training_course (registration_status);
CREATE INDEX IF NOT EXISTS idx_training_course_organizing_status ON training_course (organizing_status);
