-- ============================================================================
-- V105: Danh mục loại khóa huấn luyện đi riêng theo từng giáo phận (không dùng chung toàn hệ thống).
--       - training_course_type có diocese_id (bắt buộc). type_code vốn là UUID sinh tự động nên vẫn là khóa chính duy nhất.
--       - Danh mục hiện có được sao chép cho mọi giáo phận đang có (mã mới, giữ nguyên tên, điều kiện, khóa tiên quyết).
--       - Khóa huấn luyện, thành phần điểm và cấu hình chứng nhận đang dùng loại khóa được chuyển sang bản sao
--         của đúng giáo phận sở hữu chúng; loại khóa gốc dùng chung bị xóa.
--       - Giáo phận tạo sau này bắt đầu với danh mục rỗng.
-- ============================================================================

ALTER TABLE training_course_type ADD COLUMN IF NOT EXISTS diocese_id UUID;

-- Mỗi (loại khóa gốc, giáo phận) nhận một mã mới.
CREATE TEMP TABLE course_type_map ON COMMIT DROP AS
SELECT t.type_code AS old_code, d.id AS diocese_id, gen_random_uuid()::text AS new_code
FROM training_course_type t
         CROSS JOIN diocese d
WHERE t.diocese_id IS NULL;

INSERT INTO training_course_type (
    type_code, created_at, created_by, updated_at, updated_by, status, type_name, management_unit_type,
    required_current_level, required_min_age, required_years_active, prerequisite_type_code,
    resulting_level, certificate_short_code, max_years_after_prerequisite, diocese_id
)
SELECT m.new_code, CURRENT_TIMESTAMP, 'SYSTEM', t.updated_at, t.updated_by, t.status, t.type_name, t.management_unit_type,
       t.required_current_level, t.required_min_age, t.required_years_active, NULL,
       t.resulting_level, t.certificate_short_code, t.max_years_after_prerequisite, m.diocese_id
FROM training_course_type t
         JOIN course_type_map m ON m.old_code = t.type_code;

-- Khóa tiên quyết trỏ tới bản sao của cùng giáo phận.
UPDATE training_course_type n
SET prerequisite_type_code = pm.new_code
FROM course_type_map nm
         JOIN training_course_type o ON o.type_code = nm.old_code
         JOIN course_type_map pm ON pm.old_code = o.prerequisite_type_code AND pm.diocese_id = nm.diocese_id
WHERE n.type_code = nm.new_code
  AND o.prerequisite_type_code IS NOT NULL;

-- Khóa huấn luyện: giáo phận sở hữu là giáo phận lưu trực tiếp, hoặc giáo phận của giáo hạt (khóa giáo hạt).
UPDATE training_course c
SET course_type_code = m.new_code
FROM course_type_map m
WHERE m.old_code = c.course_type_code
  AND m.diocese_id = COALESCE(c.diocese_id, (SELECT de.diocese_id FROM deanery de WHERE de.id = c.deanery_id));

-- Thành phần điểm: giáo phận sở hữu là diocese_id hoặc giáo phận của giáo hạt chủ sở hữu.
UPDATE score_component_template s
SET course_type_code = m.new_code
FROM course_type_map m
WHERE m.old_code = s.course_type_code
  AND m.diocese_id = COALESCE(s.diocese_id, (SELECT de.diocese_id FROM deanery de WHERE de.id = s.deanery_id));

-- Gán cấu hình chứng nhận: phạm vi SYSTEM áp dụng cho mọi bản sao; DIOCESE:/DEANERY: chỉ cho bản sao của giáo phận đó.
INSERT INTO certificate_config_assignment (id, course_type_code, scope_key, config_id, created_at, created_by, updated_at, updated_by, status)
SELECT gen_random_uuid(), m.new_code, a.scope_key, a.config_id, CURRENT_TIMESTAMP, 'SYSTEM', a.updated_at, a.updated_by, a.status
FROM certificate_config_assignment a
         JOIN course_type_map m ON m.old_code = a.course_type_code
WHERE a.scope_key = 'SYSTEM'
   OR (a.scope_key LIKE 'DIOCESE:%' AND m.diocese_id::text = substring(a.scope_key FROM 9))
   OR (a.scope_key LIKE 'DEANERY:%' AND m.diocese_id = (
        SELECT de.diocese_id FROM deanery de WHERE de.id::text = substring(a.scope_key FROM 9)));

-- Loại khóa gốc còn bị tham chiếu (dữ liệu không xác định được giáo phận) giao cho giáo phận đầu tiên thay vì mất.
UPDATE training_course_type t
SET diocese_id = (SELECT d.id FROM diocese d ORDER BY d.created_at, d.id LIMIT 1)
WHERE t.diocese_id IS NULL
  AND (EXISTS (SELECT 1 FROM training_course c WHERE c.course_type_code = t.type_code)
    OR EXISTS (SELECT 1 FROM score_component_template s WHERE s.course_type_code = t.type_code));

-- Gỡ loại khóa gốc dùng chung (gán cấu hình chứng nhận cũ bị xóa theo ON DELETE CASCADE).
-- Khóa tiên quyết của các bản gốc không còn ai trỏ tới sau khi sao chép.
UPDATE training_course_type SET prerequisite_type_code = NULL
WHERE diocese_id IS NULL
   OR prerequisite_type_code IN (SELECT type_code FROM training_course_type WHERE diocese_id IS NULL);
DELETE FROM training_course_type WHERE diocese_id IS NULL;

ALTER TABLE training_course_type ALTER COLUMN diocese_id SET NOT NULL;
ALTER TABLE training_course_type
    ADD CONSTRAINT fk_training_course_type_diocese FOREIGN KEY (diocese_id) REFERENCES diocese (id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_training_course_type_diocese ON training_course_type (diocese_id);
