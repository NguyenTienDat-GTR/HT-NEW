-- ============================================================================
-- V057: Thành phần điểm gắn loại khóa + hệ số + cờ tính vào điểm tổng kết.
--       Bỏ module công thức điểm (score_formula_template / score_formula_component).
--       Điểm tổng kết = trung bình có hệ số các thành phần có include_in_final = TRUE.
--       Điểm đạt (passing_score) chuyển sang training_course.
-- ============================================================================

ALTER TABLE score_component_template ADD COLUMN IF NOT EXISTS course_type_code VARCHAR(255);
ALTER TABLE score_component_template ADD COLUMN IF NOT EXISTS coefficient NUMERIC(10, 2) NOT NULL DEFAULT 1;
ALTER TABLE score_component_template ADD COLUMN IF NOT EXISTS include_in_final BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill loại khóa của thành phần từ khóa học đang dùng công thức chứa thành phần đó.
UPDATE score_component_template sc
SET course_type_code = src.course_type_code
FROM (
    SELECT DISTINCT ON (sfc.component_id) sfc.component_id, tc.course_type_code
    FROM score_formula_component sfc
    JOIN training_course tc ON tc.score_formula_id = sfc.formula_id
    ORDER BY sfc.component_id, tc.created_at DESC NULLS LAST
) src
WHERE src.component_id = sc.id
  AND sc.course_type_code IS NULL;

-- Thành phần cũ không xác định được loại khóa thì vô hiệu để quản trị gán lại.
UPDATE score_component_template SET status = FALSE WHERE course_type_code IS NULL;

ALTER TABLE score_component_template
    ADD CONSTRAINT fk_score_component_template_course_type
        FOREIGN KEY (course_type_code) REFERENCES training_course_type (type_code);
CREATE INDEX IF NOT EXISTS idx_score_component_template_course_type ON score_component_template (course_type_code);
ALTER TABLE score_component_template
    ADD CONSTRAINT chk_score_component_template_coefficient CHECK (coefficient > 0);

-- Điểm đạt chuyển sang khóa huấn luyện.
ALTER TABLE training_course ADD COLUMN IF NOT EXISTS passing_score NUMERIC(10, 2);
UPDATE training_course tc
SET passing_score = sft.passing_score
FROM score_formula_template sft
WHERE sft.id = tc.score_formula_id;
UPDATE training_course SET passing_score = 0 WHERE passing_score IS NULL;
ALTER TABLE training_course ALTER COLUMN passing_score SET NOT NULL;

-- Bỏ công thức điểm.
ALTER TABLE training_course DROP CONSTRAINT IF EXISTS fk_training_course_score_formula_id;
DROP INDEX IF EXISTS idx_training_course_score_formula_id;
ALTER TABLE training_course DROP COLUMN IF EXISTS score_formula_id;
DROP TABLE IF EXISTS score_formula_component;
DROP TABLE IF EXISTS score_formula_template;

-- Vô hiệu quyền score_formula cũ (giữ lịch sử).
UPDATE role_permission SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.score_formula.%';
UPDATE account_permission_override SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.score_formula.%';
UPDATE permission SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.score_formula.%';
