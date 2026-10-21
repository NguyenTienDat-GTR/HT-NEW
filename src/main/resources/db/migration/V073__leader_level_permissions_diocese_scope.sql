-- ============================================================================
-- V073: Quản lý danh mục cấp độ thuộc về admin giáo phận (không phải super admin).
--       - Bỏ quyền system.leader_level.*.all (V072) và các lần gán cho vai trò / tài khoản.
--       - Thêm quyền system.leader_level.{read,create,update,delete}.diocese, chỉ gán cho ADMIN_DIOCESE.
--         Admin giáo phận phân quyền tiếp cho vai trò thấp hơn bằng cơ chế gán quyền theo phạm vi sẵn có.
-- ============================================================================

DELETE FROM role_permission
WHERE permission_code IN (SELECT permission_code FROM permission WHERE resource_code = 'leader_level' AND scope_code = 'all');

DELETE FROM account_permission_override
WHERE permission_code IN (SELECT permission_code FROM permission WHERE resource_code = 'leader_level' AND scope_code = 'all');

DELETE FROM permission WHERE resource_code = 'leader_level' AND scope_code = 'all';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('system.leader_level.read.diocese', 'Xem cấp độ huynh trưởng', 'system', 'leader_level', 'read', 'diocese',
     'Xem danh mục cấp độ huynh trưởng', TRUE, 560, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.leader_level.create.diocese', 'Tạo cấp độ huynh trưởng', 'system', 'leader_level', 'create', 'diocese',
     'Thêm cấp độ mới vào danh mục', TRUE, 561, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.leader_level.update.diocese', 'Sửa và sắp xếp cấp độ huynh trưởng', 'system', 'leader_level', 'update', 'diocese',
     'Đổi tên và kéo thả thứ tự cấp độ', TRUE, 562, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.leader_level.delete.diocese', 'Xóa cấp độ huynh trưởng', 'system', 'leader_level', 'delete', 'diocese',
     'Xóa cấp độ chưa được sử dụng', TRUE, 563, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, status = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

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
JOIN permission p ON p.resource_code = 'leader_level' AND p.scope_code = 'diocese'
WHERE r.role_code = 'ADMIN_DIOCESE'
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW', expires_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
