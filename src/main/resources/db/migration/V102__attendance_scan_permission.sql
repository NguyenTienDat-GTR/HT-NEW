-- ============================================================================
-- V102: Quyền quét / xác nhận điểm danh là quyền riêng theo phạm vi (không dùng quyền duyệt danh sách đăng ký).
--       training.attendance.scan.{diocese,deanery}: tra cứu mã QR, xác nhận điểm danh và điểm danh / bỏ điểm danh thủ công
--       cho khóa do đúng đơn vị của tài khoản tổ chức.
-- ============================================================================

INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('scan', 'Quét điểm danh', 'Quét mã QR và xác nhận điểm danh', 60, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE SET status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission_resource (
    resource_code, module_code, resource_name, description, display_order,
    status, created_at, created_by, updated_at, updated_by
)
VALUES ('attendance', 'training', 'Điểm danh khóa huấn luyện', 'Quét mã QR và xác nhận điểm danh của hồ sơ trong khóa huấn luyện', 47,
        TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (resource_code) DO UPDATE
SET module_code = EXCLUDED.module_code, resource_name = EXCLUDED.resource_name, description = EXCLUDED.description,
    display_order = EXCLUDED.display_order, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.attendance.scan.diocese', 'Quét điểm danh - Giáo phận', 'training', 'attendance', 'scan', 'diocese',
     'Quét mã QR và xác nhận điểm danh cho khóa huấn luyện do giáo phận tổ chức', TRUE, 540, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.attendance.scan.deanery', 'Quét điểm danh - Giáo hạt', 'training', 'attendance', 'scan', 'deanery',
     'Quét mã QR và xác nhận điểm danh cho khóa huấn luyện do giáo hạt tổ chức', TRUE, 541, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, action_code = EXCLUDED.action_code,
    is_system = TRUE, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

-- Mặc định gán cho admin giáo phận (cấp giáo phận) và admin giáo hạt (cấp giáo hạt); các tài khoản khác được cấp riêng.
INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, r.role_code, p.permission_code, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'SYSTEM', CURRENT_TIMESTAMP
FROM role r
JOIN permission p ON p.resource_code = 'attendance'
WHERE (r.role_code = 'ADMIN_DIOCESE' AND p.scope_code = 'diocese')
   OR (r.role_code = 'ADMIN_DEANERY' AND p.scope_code = 'deanery')
ON CONFLICT (role_code, permission_code) DO UPDATE
SET expires_at = NULL, revoked_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
