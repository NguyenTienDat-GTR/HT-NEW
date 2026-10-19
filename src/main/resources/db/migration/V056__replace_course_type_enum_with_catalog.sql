-- ============================================================================
-- V056: Thay enum CourseType bằng catalog training_course_type (quản lý được),
--       gộp điều kiện khóa (course_requirement) vào loại khóa, và cập nhật
--       training_course trỏ tới loại khóa. Chuyển quyền training.requirement.*
--       sang training.course_type.* và vô hiệu quyền requirement cũ.
-- ============================================================================

-- 1) Bảng catalog loại khóa (toàn hệ thống)
CREATE TABLE IF NOT EXISTS training_course_type
(
    type_code              VARCHAR(255) PRIMARY KEY,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(100),
    updated_at             TIMESTAMP WITH TIME ZONE,
    updated_by             VARCHAR(100),
    status                 BOOLEAN                  NOT NULL DEFAULT TRUE,
    type_name              VARCHAR(255),
    management_unit_type   VARCHAR(50)              NOT NULL,
    required_current_level VARCHAR(50),
    required_min_age       INTEGER,
    required_years_active  INTEGER,
    prerequisite_type_code VARCHAR(255),
    resulting_level        VARCHAR(50),
    certificate_short_code VARCHAR(100),
    CONSTRAINT fk_training_course_type_prerequisite
        FOREIGN KEY (prerequisite_type_code) REFERENCES training_course_type (type_code)
);

-- 2) Seed 14 loại khóa hiện hữu (giữ nguyên hành vi scope/chứng chỉ trước đây).
--    prerequisite_type_code để NULL rồi cập nhật ở bước sau để tránh lỗi thứ tự khóa ngoại.
INSERT INTO training_course_type
    (type_code, type_name, management_unit_type, resulting_level, certificate_short_code,
     status, created_at, created_by, updated_at, updated_by)
VALUES
    ('SMTL_DT',   'SMTL_DT',   'DEANERY', NULL,        'DT',      TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMKN_DT',   'SMKN_DT',   'DEANERY', 'DU_TRUONG', 'DT',      TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMTL_I',    'SMTL_I',    'DIOCESE', NULL,        'SMTLI',   TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMKN_I',    'SMKN_I',    'DIOCESE', 'HT_I',      'SMKNI',   TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('TSM_II',    'TSM_II',    'DIOCESE', NULL,        'TSMII',   TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMTL_II',   'SMTL_II',   'DIOCESE', NULL,        'SMTLII',  TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMKN_II',   'SMKN_II',   'DIOCESE', 'HT_II',     'SMKNII',  TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('TSM_III_1', 'TSM_III_1', 'DIOCESE', NULL,        'TSMIII1', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('TSM_III_2', 'TSM_III_2', 'DIOCESE', NULL,        'TSMIII2', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMTL_III',  'SMTL_III',  'DIOCESE', NULL,        'III',     TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMKN_III',  'SMKN_III',  'DIOCESE', 'HT_III',    'III',     TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('HLV_I',     'HLV_I',     'DIOCESE', 'HLV_I',     'HLVI',    TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('HLV_II',    'HLV_II',    'DIOCESE', 'HLV_II',    'HLVII',   TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('HLV_III',   'HLV_III',   'DIOCESE', 'HLV_III',   'HLVIII',  TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (type_code) DO NOTHING;

-- 2b) Chuỗi khóa tiên quyết
UPDATE training_course_type SET prerequisite_type_code = 'SMTL_DT' WHERE type_code = 'SMKN_DT';
UPDATE training_course_type SET prerequisite_type_code = 'SMTL_I'  WHERE type_code = 'SMKN_I';
UPDATE training_course_type SET prerequisite_type_code = 'SMKN_I'  WHERE type_code = 'TSM_II';
UPDATE training_course_type SET prerequisite_type_code = 'TSM_II'  WHERE type_code = 'SMTL_II';
UPDATE training_course_type SET prerequisite_type_code = 'SMTL_II' WHERE type_code = 'SMKN_II';
UPDATE training_course_type SET prerequisite_type_code = 'SMKN_II' WHERE type_code = 'TSM_III_1';
UPDATE training_course_type SET prerequisite_type_code = 'TSM_III_1' WHERE type_code = 'TSM_III_2';
UPDATE training_course_type SET prerequisite_type_code = 'TSM_III_2' WHERE type_code = 'SMTL_III';
UPDATE training_course_type SET prerequisite_type_code = 'SMTL_III' WHERE type_code = 'SMKN_III';

-- 3) Backfill điều kiện tham dự từ course_requirement (gộp điều kiện vào loại khóa).
--    Mỗi loại khóa lấy điều kiện từ một bản ghi requirement tương ứng (bản mới nhất).
UPDATE training_course_type ct
SET required_current_level = src.required_current_level,
    required_min_age       = src.required_min_age,
    required_years_active  = src.required_years_active
FROM (
    SELECT DISTINCT ON (course_type)
        course_type,
        required_current_level,
        required_min_age,
        required_years_active
    FROM course_requirement
    ORDER BY course_type, updated_at DESC NULLS LAST, requirement_code
) src
WHERE src.course_type = ct.type_code;

-- 4) training_course: thêm FK course_type_code, backfill từ enum cũ, bỏ requirement + course_type enum.
ALTER TABLE training_course ADD COLUMN IF NOT EXISTS course_type_code VARCHAR(255);
UPDATE training_course SET course_type_code = course_type WHERE course_type_code IS NULL;

ALTER TABLE training_course DROP CONSTRAINT IF EXISTS fk_training_course_requirement;
DROP INDEX IF EXISTS idx_training_course_requirement_code;
ALTER TABLE training_course DROP COLUMN IF EXISTS requirement_code;
ALTER TABLE training_course DROP COLUMN IF EXISTS course_type;

ALTER TABLE training_course ALTER COLUMN course_type_code SET NOT NULL;
ALTER TABLE training_course
    ADD CONSTRAINT fk_training_course_type
        FOREIGN KEY (course_type_code) REFERENCES training_course_type (type_code);
CREATE INDEX IF NOT EXISTS idx_training_course_type_code ON training_course (course_type_code);

-- 5) Bỏ bảng điều kiện khóa cũ (đã gộp vào loại khóa).
DROP TABLE IF EXISTS course_requirement;

-- ============================================================================
-- 6) RBAC: thêm resource + quyền training.course_type.*, chuyển grant từ
--    training.requirement.*, rồi vô hiệu quyền requirement cũ.
-- ============================================================================
INSERT INTO permission_resource
    (resource_code, module_code, resource_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('course_type', 'training', 'Loại khóa huấn luyện', 'Quản lý catalog loại khóa huấn luyện và điều kiện tham dự', 25, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (resource_code) DO UPDATE
SET module_code = EXCLUDED.module_code,
    resource_name = EXCLUDED.resource_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission
    (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('training.course_type.create.diocese', 'Tạo loại khóa cấp giáo phận', 'training', 'course_type', 'create', 'diocese', 'Tạo loại khóa huấn luyện do giáo phận quản lý', TRUE, 91, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.read.diocese',   'Xem loại khóa cấp giáo phận', 'training', 'course_type', 'read',   'diocese', 'Xem loại khóa huấn luyện do giáo phận quản lý', TRUE, 92, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.update.diocese', 'Cập nhật loại khóa cấp giáo phận', 'training', 'course_type', 'update', 'diocese', 'Cập nhật loại khóa huấn luyện do giáo phận quản lý', TRUE, 93, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.toggle.diocese', 'Đổi trạng thái loại khóa cấp giáo phận', 'training', 'course_type', 'toggle', 'diocese', 'Đổi trạng thái loại khóa huấn luyện do giáo phận quản lý', TRUE, 94, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.create.deanery', 'Tạo loại khóa cấp giáo hạt', 'training', 'course_type', 'create', 'deanery', 'Tạo loại khóa huấn luyện do giáo hạt quản lý (khóa DT)', TRUE, 95, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.read.deanery',   'Xem loại khóa cấp giáo hạt', 'training', 'course_type', 'read',   'deanery', 'Xem loại khóa huấn luyện do giáo hạt quản lý (khóa DT)', TRUE, 96, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.update.deanery', 'Cập nhật loại khóa cấp giáo hạt', 'training', 'course_type', 'update', 'deanery', 'Cập nhật loại khóa huấn luyện do giáo hạt quản lý (khóa DT)', TRUE, 97, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course_type.toggle.deanery', 'Đổi trạng thái loại khóa cấp giáo hạt', 'training', 'course_type', 'toggle', 'deanery', 'Đổi trạng thái loại khóa huấn luyện do giáo hạt quản lý (khóa DT)', TRUE, 98, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

-- Chuyển grant role_permission từ training.requirement.* sang training.course_type.* (giữ effect/status).
INSERT INTO role_permission
    (id, role_code, permission_code, effect, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by)
SELECT
    (
        substr(md5(rp.role_code || ':' || replace(rp.permission_code, 'training.requirement.', 'training.course_type.')), 1, 8) || '-' ||
        substr(md5(rp.role_code || ':' || replace(rp.permission_code, 'training.requirement.', 'training.course_type.')), 9, 4) || '-' ||
        substr(md5(rp.role_code || ':' || replace(rp.permission_code, 'training.requirement.', 'training.course_type.')), 13, 4) || '-' ||
        substr(md5(rp.role_code || ':' || replace(rp.permission_code, 'training.requirement.', 'training.course_type.')), 17, 4) || '-' ||
        substr(md5(rp.role_code || ':' || replace(rp.permission_code, 'training.requirement.', 'training.course_type.')), 21, 12)
    )::uuid,
    rp.role_code,
    replace(rp.permission_code, 'training.requirement.', 'training.course_type.'),
    rp.effect,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    rp.status,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM role_permission rp
WHERE rp.permission_code LIKE 'training.requirement.%'
  AND EXISTS (SELECT 1 FROM permission p WHERE p.permission_code = replace(rp.permission_code, 'training.requirement.', 'training.course_type.'))
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp2
      WHERE rp2.role_code = rp.role_code
        AND rp2.permission_code = replace(rp.permission_code, 'training.requirement.', 'training.course_type.')
  );

-- Vô hiệu quyền requirement cũ (giữ lịch sử, không xóa).
UPDATE role_permission SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.requirement.%';
UPDATE account_permission_override SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.requirement.%';
UPDATE permission SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE permission_code LIKE 'training.requirement.%';
