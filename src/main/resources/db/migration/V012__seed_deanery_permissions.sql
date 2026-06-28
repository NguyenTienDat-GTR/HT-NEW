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
    'deanery',
    'organization',
    'Deanery',
    'Deanery administration with diocese and deanery scope-aware access',
    20,
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
        'organization.deanery.create.all',
        'Create deaneries',
        'organization',
        'deanery',
        'create',
        'all',
        'Create deaneries across the entire system',
        TRUE,
        10,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.read.all',
        'Read all deaneries',
        'organization',
        'deanery',
        'read',
        'all',
        'Read any deanery in the system',
        TRUE,
        20,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.update.all',
        'Update all deaneries',
        'organization',
        'deanery',
        'update',
        'all',
        'Update any deanery in the system',
        TRUE,
        30,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.delete.all',
        'Delete deaneries',
        'organization',
        'deanery',
        'delete',
        'all',
        'Delete any deanery in the system',
        TRUE,
        40,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.create.diocese',
        'Create deaneries in own diocese',
        'organization',
        'deanery',
        'create',
        'diocese',
        'Create deaneries inside the diocese resolved from the account leader hierarchy',
        TRUE,
        50,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.read.diocese',
        'Read deaneries in own diocese',
        'organization',
        'deanery',
        'read',
        'diocese',
        'Read deaneries inside the diocese resolved from the account leader hierarchy',
        TRUE,
        60,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.update.diocese',
        'Update deaneries in own diocese',
        'organization',
        'deanery',
        'update',
        'diocese',
        'Update deaneries inside the diocese resolved from the account leader hierarchy',
        TRUE,
        70,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.delete.diocese',
        'Delete deaneries in own diocese',
        'organization',
        'deanery',
        'delete',
        'diocese',
        'Delete deaneries inside the diocese resolved from the account leader hierarchy',
        TRUE,
        80,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.read.deanery',
        'Read own deanery',
        'organization',
        'deanery',
        'read',
        'deanery',
        'Read the deanery resolved from the account leader hierarchy',
        TRUE,
        90,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.deanery.update.deanery',
        'Update own deanery',
        'organization',
        'deanery',
        'update',
        'deanery',
        'Update the deanery resolved from the account leader hierarchy',
        TRUE,
        100,
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
    'organization.deanery.create.all',
    'organization.deanery.read.all',
    'organization.deanery.update.all',
    'organization.deanery.delete.all',
    'organization.deanery.create.diocese',
    'organization.deanery.read.diocese',
    'organization.deanery.update.diocese',
    'organization.deanery.delete.diocese',
    'organization.deanery.read.deanery',
    'organization.deanery.update.deanery'
)
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = 'SUPER_ADMIN'
      AND rp.permission_code = p.permission_code
);
