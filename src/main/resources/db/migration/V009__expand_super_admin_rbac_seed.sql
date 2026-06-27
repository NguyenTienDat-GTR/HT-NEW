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
    'System-wide administrator with full RBAC access across all modules and resources',
    TRUE,
    1,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (role_code) DO UPDATE
SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

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
VALUES
    (
        'auth',
        'Authentication',
        'Authentication and authorization module',
        5,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'system',
        'System',
        'System administration and RBAC module',
        10,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    )
ON CONFLICT (module_code) DO UPDATE
SET
    module_name = EXCLUDED.module_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

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
VALUES
    (
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
    ),
    (
        'role',
        'system',
        'Role',
        'RBAC role management',
        10,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'permission',
        'system',
        'Permission',
        'RBAC permission management',
        20,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'account',
        'system',
        'Account',
        'System account administration',
        30,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'audit_log',
        'system',
        'Audit Log',
        'Audit log visibility and review',
        40,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    )
ON CONFLICT (resource_code) DO UPDATE
SET
    module_code = EXCLUDED.module_code,
    resource_name = EXCLUDED.resource_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

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
VALUES
    (
        'auth.session.manage.all',
        'Manage authentication sessions',
        'auth',
        'session',
        'manage',
        'all',
        'Manage bootstrap, refresh, login session, and authentication administration flows',
        TRUE,
        5,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'system.role.manage.all',
        'Manage system roles',
        'system',
        'role',
        'manage',
        'all',
        'Manage RBAC roles across the entire system',
        TRUE,
        10,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'system.permission.manage.all',
        'Manage system permissions',
        'system',
        'permission',
        'manage',
        'all',
        'Manage RBAC permissions, mappings, and policy structure across the entire system',
        TRUE,
        20,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'system.account.manage.all',
        'Manage system accounts',
        'system',
        'account',
        'manage',
        'all',
        'Manage system accounts and access assignments across the entire system',
        TRUE,
        30,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'system.audit_log.read.all',
        'Read audit logs',
        'system',
        'audit_log',
        'read',
        'all',
        'View audit logs and access history across the entire system',
        TRUE,
        40,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    )
ON CONFLICT (permission_code) DO UPDATE
SET
    permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
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
        substr(md5('SUPER_ADMIN:' || p.permission_code), 1, 8) || '-' ||
        substr(md5('SUPER_ADMIN:' || p.permission_code), 9, 4) || '-' ||
        substr(md5('SUPER_ADMIN:' || p.permission_code), 13, 4) || '-' ||
        substr(md5('SUPER_ADMIN:' || p.permission_code), 17, 4) || '-' ||
        substr(md5('SUPER_ADMIN:' || p.permission_code), 21, 12)
    )::uuid,
    'SUPER_ADMIN',
    p.permission_code,
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM permission p
WHERE p.status = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = 'SUPER_ADMIN'
      AND rp.permission_code = p.permission_code
);
