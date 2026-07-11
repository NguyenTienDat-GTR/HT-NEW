INSERT INTO permission (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('organization.leader.delete.parish', 'Xóa huynh trưởng trong giáo xứ', 'organization', 'leader', 'delete', 'parish', 'Cho phép xóa huynh trưởng trong giáo xứ được phân quyền', TRUE, 0, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO role_permission (id, role_code, permission_code, effect, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by)
SELECT (substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' || substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' || substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' || substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' || substr(md5(r.role_code || ':' || p.permission_code), 21, 12))::uuid, r.role_code, p.permission_code, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role r JOIN permission p ON p.permission_code = 'organization.leader.delete.parish'
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_PARISH')
ON CONFLICT (id) DO NOTHING;
