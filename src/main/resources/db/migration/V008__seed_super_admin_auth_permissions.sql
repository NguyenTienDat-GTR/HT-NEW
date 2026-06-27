INSERT INTO role (
    role_code,
    role_name,
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
    'SUPER_ADMIN',
    'Super Administrator',
    'Bootstrap role for full authentication administration',
    TRUE,
    1,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO permission_module (
    module_code,
    module_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES (
    'auth',
    'Authentication',
    'Authentication and authorization module',
    5,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (module_code) DO NOTHING;

INSERT INTO permission_resource (
    resource_code,
    module_code,
    resource_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES (
    'session',
    'auth',
    'Session',
    'Authentication session management',
    5,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (resource_code) DO NOTHING;

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
    'auth.session.manage.all',
    'Manage authentication sessions',
    'auth',
    'session',
    'manage',
    'all',
    'Manage bootstrap, refresh, and admin auth operations',
    TRUE,
    5,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (permission_code) DO NOTHING;

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
    'SUPER_ADMIN_AUTH_SESSION_MANAGE_ALL',
    'SUPER_ADMIN',
    'auth.session.manage.all',
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permission
    WHERE role_code = 'SUPER_ADMIN'
      AND permission_code = 'auth.session.manage.all'
);
