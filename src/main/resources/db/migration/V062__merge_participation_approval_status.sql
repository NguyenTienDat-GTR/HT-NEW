-- ============================================================================
-- V062: Gộp duyệt giáo hạt và duyệt giáo phận thành một trạng thái duyệt đăng ký:
--       khóa giáo hạt do giáo hạt duyệt, khóa giáo phận do giáo phận duyệt.
-- ============================================================================

ALTER TABLE course_participation ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50);

UPDATE course_participation cp
SET approval_status = CASE
        WHEN t.management_unit_type = 'DEANERY' THEN cp.deanery_approval_status
        ELSE cp.diocese_approval_status
    END
FROM training_course c
JOIN training_course_type t ON t.type_code = c.course_type_code
WHERE c.course_code = cp.course_id;

ALTER TABLE course_participation DROP COLUMN IF EXISTS deanery_approval_status;
ALTER TABLE course_participation DROP COLUMN IF EXISTS diocese_approval_status;
ALTER TABLE course_participation DROP COLUMN IF EXISTS deanery_submitted_at;
