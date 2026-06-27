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
    'organization',
    'Organization',
    'Organization structure management module',
    15,
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
VALUES (
    'diocese',
    'organization',
    'Diocese',
    'Diocese administration and scope-aware access',
    10,
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
        'organization.diocese.create.all',
        'Create dioceses',
        'organization',
        'diocese',
        'create',
        'all',
        'Create dioceses across the entire system',
        TRUE,
        10,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.diocese.read.all',
        'Read all dioceses',
        'organization',
        'diocese',
        'read',
        'all',
        'Read any diocese in the system',
        TRUE,
        20,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.diocese.update.all',
        'Update all dioceses',
        'organization',
        'diocese',
        'update',
        'all',
        'Update any diocese in the system',
        TRUE,
        30,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.diocese.delete.all',
        'Delete dioceses',
        'organization',
        'diocese',
        'delete',
        'all',
        'Delete any diocese in the system',
        TRUE,
        40,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.diocese.read.diocese',
        'Read own diocese',
        'organization',
        'diocese',
        'read',
        'diocese',
        'Read the diocese resolved from the account leader hierarchy',
        TRUE,
        50,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.diocese.update.diocese',
        'Update own diocese',
        'organization',
        'diocese',
        'update',
        'diocese',
        'Update the diocese resolved from the account leader hierarchy',
        TRUE,
        60,
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
WHERE p.permission_code IN (
    'organization.diocese.create.all',
    'organization.diocese.read.all',
    'organization.diocese.update.all',
    'organization.diocese.delete.all',
    'organization.diocese.read.diocese',
    'organization.diocese.update.diocese'
)
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = 'SUPER_ADMIN'
      AND rp.permission_code = p.permission_code
);
