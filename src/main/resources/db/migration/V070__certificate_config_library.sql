-- ============================================================================
-- V070: Thư viện cấu hình chứng nhận.
--       - Mỗi cấu hình có tên, thuộc một phạm vi sở hữu (scope_key): SYSTEM | DIOCESE:<uuid> | DEANERY:<uuid>.
--       - Mỗi phạm vi có đúng một cấu hình chung (is_default); không xóa được cấu hình chung.
--       - Một cấu hình gán được cho nhiều loại khóa; mỗi loại khóa chỉ dùng một cấu hình trong một phạm vi
--         (bảng certificate_config_assignment). Loại khóa chưa gán dùng cấu hình chung.
--       - Quyền CRUD theo phạm vi giáo phận / giáo hạt.
-- ============================================================================

ALTER TABLE certificate_config
    ADD COLUMN IF NOT EXISTS name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS scope_key VARCHAR(100) NOT NULL DEFAULT 'SYSTEM';

CREATE TABLE IF NOT EXISTS certificate_config_assignment
(
    id               UUID PRIMARY KEY,
    course_type_code VARCHAR(255) NOT NULL,
    scope_key        VARCHAR(100) NOT NULL,
    config_id        UUID         NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMP WITH TIME ZONE,
    updated_by       VARCHAR(100),
    status           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cert_config_assignment_type FOREIGN KEY (course_type_code)
        REFERENCES training_course_type (type_code) ON DELETE CASCADE,
    CONSTRAINT fk_cert_config_assignment_config FOREIGN KEY (config_id)
        REFERENCES certificate_config (id) ON DELETE CASCADE,
    CONSTRAINT uk_cert_config_assignment UNIQUE (course_type_code, scope_key)
);

CREATE INDEX IF NOT EXISTS idx_cert_config_assignment_config ON certificate_config_assignment (config_id);

-- Dữ liệu cũ: nếu hệ thống chỉ có đúng một giáo phận thì cấu hình cũ thuộc giáo phận đó, ngược lại thuộc SYSTEM.
UPDATE certificate_config
SET scope_key = COALESCE(
        (SELECT 'DIOCESE:' || d.id::text FROM diocese d WHERE (SELECT COUNT(*) FROM diocese) = 1 LIMIT 1),
        'SYSTEM');

UPDATE certificate_config c
SET name = COALESCE(
        CASE WHEN c.course_type_code IS NULL THEN 'Cấu hình chung'
             ELSE (SELECT t.type_name FROM training_course_type t WHERE t.type_code = c.course_type_code) END,
        c.course_type_code,
        'Cấu hình'),
    is_default = (c.course_type_code IS NULL);

-- Tên không được trùng trong cùng phạm vi (hiếm khi xảy ra với dữ liệu cũ): thêm hậu tố số.
UPDATE certificate_config c
SET name = c.name || ' (' || d.rn || ')'
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY scope_key, lower(name) ORDER BY created_at, id) - 1 AS rn
    FROM certificate_config
) d
WHERE d.id = c.id AND d.rn > 0;

ALTER TABLE certificate_config ALTER COLUMN name SET NOT NULL;

-- Cấu hình riêng của loại khóa trước đây -> bản ghi gán.
INSERT INTO certificate_config_assignment (id, course_type_code, scope_key, config_id, created_at, created_by, status)
SELECT (
           substr(md5(c.id::text || ':' || c.course_type_code), 1, 8) || '-' ||
           substr(md5(c.id::text || ':' || c.course_type_code), 9, 4) || '-' ||
           substr(md5(c.id::text || ':' || c.course_type_code), 13, 4) || '-' ||
           substr(md5(c.id::text || ':' || c.course_type_code), 17, 4) || '-' ||
           substr(md5(c.id::text || ':' || c.course_type_code), 21, 12)
       )::uuid,
       c.course_type_code, c.scope_key, c.id, CURRENT_TIMESTAMP, 'SYSTEM', TRUE
FROM certificate_config c
WHERE c.course_type_code IS NOT NULL;

-- Bỏ ràng buộc gắn cấu hình với đúng một loại khóa.
DROP INDEX IF EXISTS uq_certificate_config_default;
ALTER TABLE certificate_config DROP CONSTRAINT IF EXISTS uk_certificate_config_course_type;
ALTER TABLE certificate_config DROP CONSTRAINT IF EXISTS fk_certificate_config_course_type;
ALTER TABLE certificate_config DROP COLUMN IF EXISTS course_type_code;

-- Mỗi phạm vi tối đa một cấu hình chung, tên duy nhất (không phân biệt hoa thường) trong phạm vi.
CREATE UNIQUE INDEX uq_certificate_config_scope_default ON certificate_config (scope_key) WHERE is_default;
CREATE UNIQUE INDEX uq_certificate_config_scope_name ON certificate_config (scope_key, lower(name));

-- ----------------------------------------------------------------------------
-- Quyền CRUD theo phạm vi giáo phận / giáo hạt.
-- ----------------------------------------------------------------------------
INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('create', 'Tạo mới', 'Tạo mới bản ghi', 10, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('delete', 'Xóa', 'Xóa bản ghi', 40, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE SET status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.certificate_config.read.diocese', 'Xem cấu hình chứng nhận - Giáo phận', 'training', 'certificate_config', 'read', 'diocese',
     'Xem thư viện cấu hình chứng nhận của giáo phận', TRUE, 500, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.create.diocese', 'Tạo cấu hình chứng nhận - Giáo phận', 'training', 'certificate_config', 'create', 'diocese',
     'Tạo cấu hình chứng nhận trong thư viện của giáo phận', TRUE, 502, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.update.diocese', 'Cập nhật cấu hình chứng nhận - Giáo phận', 'training', 'certificate_config', 'update', 'diocese',
     'Sửa, đặt cấu hình chung và gán cấu hình cho loại khóa trong giáo phận', TRUE, 501, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.delete.diocese', 'Xóa cấu hình chứng nhận - Giáo phận', 'training', 'certificate_config', 'delete', 'diocese',
     'Xóa cấu hình chứng nhận trong thư viện của giáo phận', TRUE, 503, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.read.deanery', 'Xem cấu hình chứng nhận - Giáo hạt', 'training', 'certificate_config', 'read', 'deanery',
     'Xem thư viện cấu hình chứng nhận của giáo hạt', TRUE, 510, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.create.deanery', 'Tạo cấu hình chứng nhận - Giáo hạt', 'training', 'certificate_config', 'create', 'deanery',
     'Tạo cấu hình chứng nhận trong thư viện của giáo hạt', TRUE, 512, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.update.deanery', 'Cập nhật cấu hình chứng nhận - Giáo hạt', 'training', 'certificate_config', 'update', 'deanery',
     'Sửa, đặt cấu hình chung và gán cấu hình cho loại khóa trong giáo hạt', TRUE, 511, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.delete.deanery', 'Xóa cấu hình chứng nhận - Giáo hạt', 'training', 'certificate_config', 'delete', 'deanery',
     'Xóa cấu hình chứng nhận trong thư viện của giáo hạt', TRUE, 513, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    action_code = EXCLUDED.action_code,
    is_system = TRUE,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO role_permission (
    id, role_code, permission_code, effect, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by
)
SELECT
    (
        substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 21, 12)
    )::uuid,
    r.role_code, p.permission_code, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role r
JOIN permission p ON p.resource_code = 'certificate_config'
WHERE (
        r.role_code = 'SUPER_ADMIN' AND p.scope_code = 'diocese'
        OR r.role_code = 'ADMIN_DIOCESE' AND p.scope_code = 'diocese'
        OR r.role_code = 'ADMIN_DEANERY' AND p.scope_code = 'deanery'
    )
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW', expires_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
