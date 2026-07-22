INSERT INTO permission (
    permission_code,
    permission_name,
    module_code,
    resource_code,
    action_code,
    scope_code,
    description,
    is_system,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES (
    'system.account_role.toggle.all',
    'Đổi trạng thái vai trò tài khoản - Toàn hệ thống',
    'system',
    'account_role',
    'toggle',
    'all',
    'Cho phép bật hoặc tạm ngưng vai trò đã gán cho tài khoản trong toàn bộ hệ thống.',
    TRUE,
    461,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
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

INSERT INTO role_permission (
    id,
    role_code,
    permission_code,
    effect,
    assigned_by,
    assigned_at,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    (
        substr(md5('SUPER_ADMIN:system.account_role.toggle.all'), 1, 8) || '-' ||
        substr(md5('SUPER_ADMIN:system.account_role.toggle.all'), 9, 4) || '-' ||
        substr(md5('SUPER_ADMIN:system.account_role.toggle.all'), 13, 4) || '-' ||
        substr(md5('SUPER_ADMIN:system.account_role.toggle.all'), 17, 4) || '-' ||
        substr(md5('SUPER_ADMIN:system.account_role.toggle.all'), 21, 12)
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
JOIN permission p ON p.permission_code = 'system.account_role.toggle.all'
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW',
    assigned_by = 'SYSTEM',
    assigned_at = COALESCE(role_permission.assigned_at, CURRENT_TIMESTAMP),
    expires_at = NULL,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
