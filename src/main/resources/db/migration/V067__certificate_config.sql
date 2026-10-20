-- Cấu hình chứng nhận cho loại khóa có yêu cầu chứng nhận: người ký + ảnh nền + bố cục mail merge.
-- course_type_code NULL = cấu hình chung mặc định (dùng khi loại khóa chưa có cấu hình riêng).

CREATE TABLE certificate_config
(
    id               UUID PRIMARY KEY,
    course_type_code VARCHAR(255),
    signer_type      VARCHAR(20),
    signer_name      VARCHAR(255),
    signer_title     VARCHAR(255),
    background_image TEXT,
    field_layout     JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMP WITH TIME ZONE,
    updated_by       VARCHAR(100),
    status           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_certificate_config_course_type FOREIGN KEY (course_type_code)
        REFERENCES training_course_type (type_code),
    CONSTRAINT uk_certificate_config_course_type UNIQUE (course_type_code)
);

-- Chỉ cho phép một hàng cấu hình chung (course_type_code IS NULL); UNIQUE ở trên không chặn nhiều NULL.
CREATE UNIQUE INDEX uq_certificate_config_default
    ON certificate_config ((course_type_code IS NULL))
    WHERE course_type_code IS NULL;

-- Resource mới cho nhóm quyền cấu hình chứng nhận.
INSERT INTO permission_resource (
    resource_code, module_code, resource_name, description, display_order,
    status, created_at, created_by, updated_at, updated_by
)
VALUES (
    'certificate_config', 'training', 'Certificate configuration',
    'Cấu hình người ký và mail merge cho chứng nhận', 45,
    TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
)
ON CONFLICT (resource_code) DO UPDATE
SET module_code = EXCLUDED.module_code,
    resource_name = EXCLUDED.resource_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

-- Quyền đọc và quản lý cấu hình chứng nhận ở phạm vi giáo phận.
INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.certificate_config.read.diocese', 'Xem cấu hình chứng nhận - Giáo phận',
     'training', 'certificate_config', 'read', 'diocese',
     'Xem cấu hình người ký và mail merge chứng nhận trong giáo phận', TRUE, 500, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate_config.update.diocese', 'Cập nhật cấu hình chứng nhận - Giáo phận',
     'training', 'certificate_config', 'update', 'diocese',
     'Tạo và cập nhật cấu hình người ký và mail merge chứng nhận trong giáo phận', TRUE, 501, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = TRUE,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

-- Gán quyền cho super admin và admin giáo phận.
INSERT INTO role_permission (
    id, role_code, permission_code, effect, assigned_by, assigned_at,
    status, created_at, created_by, updated_at, updated_by
)
SELECT
    (
        substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 21, 12)
    )::uuid,
    r.role_code,
    p.permission_code,
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM role r
JOIN permission p ON p.permission_code IN (
    'training.certificate_config.read.diocese',
    'training.certificate_config.update.diocese'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_DIOCESE')
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW',
    assigned_by = 'SYSTEM',
    assigned_at = COALESCE(role_permission.assigned_at, CURRENT_TIMESTAMP),
    expires_at = NULL,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
