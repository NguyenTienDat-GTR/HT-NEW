-- ============================================================================
-- V117: Thành phần điểm thuộc trực tiếp từng khóa huấn luyện (tên, điểm tối đa, hệ số, cờ tính tổng kết, thứ tự).
--       Bỏ thư viện thành phần điểm mẫu (score_component_template): thành phần được khai báo ngay khi tạo/sửa khóa.
--       Điểm đã nhập (course_participation_component_score) trỏ sang thành phần của chính khóa.
-- ============================================================================

ALTER TABLE training_course_score_component ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE training_course_score_component ADD COLUMN IF NOT EXISTS description TEXT;

UPDATE training_course_score_component cc
SET name = sc.name,
    description = sc.description
FROM score_component_template sc
WHERE sc.id = cc.component_id;

UPDATE training_course_score_component SET name = 'Thành phần điểm' WHERE name IS NULL;
ALTER TABLE training_course_score_component ALTER COLUMN name SET NOT NULL;

-- Điểm đã nhập: trỏ từ thành phần mẫu sang thành phần của khóa (cùng khóa của hồ sơ).
ALTER TABLE course_participation_component_score DROP CONSTRAINT IF EXISTS fk_course_participation_component_score_component_id;

UPDATE course_participation_component_score s
SET component_id = cc.id
FROM course_participation cp,
     training_course_score_component cc
WHERE cp.id = s.participation_id
  AND cc.course_code = cp.course_id
  AND cc.component_id = s.component_id;

-- Dòng điểm mồ côi (hồ sơ không còn gắn khóa) không còn ý nghĩa.
DELETE FROM course_participation_component_score
WHERE component_id NOT IN (SELECT id FROM training_course_score_component);

ALTER TABLE course_participation_component_score
    ADD CONSTRAINT fk_course_participation_component_score_course_component
        FOREIGN KEY (component_id) REFERENCES training_course_score_component (id) ON DELETE CASCADE;

-- Bỏ liên kết tới thành phần mẫu.
ALTER TABLE training_course_score_component DROP CONSTRAINT IF EXISTS uk_tcsc_course_component;
ALTER TABLE training_course_score_component DROP CONSTRAINT IF EXISTS fk_tcsc_component;
DROP INDEX IF EXISTS idx_tcsc_component_id;
ALTER TABLE training_course_score_component DROP COLUMN IF EXISTS component_id;

DROP TABLE IF EXISTS score_component_template CASCADE;

-- Vô hiệu quyền quản lý thành phần điểm mẫu cũ (giữ lịch sử).
UPDATE role_permission SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.score_component.%';
UPDATE account_permission_override SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.score_component.%';
UPDATE permission SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.score_component.%';
