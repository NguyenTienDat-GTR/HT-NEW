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
        'organization.leader.read.diocese',
        'Read leaders in own diocese',
        'organization',
        'leader',
        'read',
        'diocese',
        'Read leaders inside the active diocese',
        TRUE,
        739,
        TRUE,
        CURRENT_TIMESTAMP,
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'SYSTEM'
    ),
    (
        'organization.leader.read.deanery',
        'Read leaders in own deanery',
        'organization',
        'leader',
        'read',
        'deanery',
        'Read leaders inside the active deanery',
        TRUE,
        740,
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
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 1, 8) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 9, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 13, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 17, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 21, 12)
    )::uuid,
    grant_row.role_code,
    grant_row.permission_code,
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM (VALUES
    ('ADMIN_DIOCESE', 'organization.diocese.read.diocese'),
    ('ADMIN_DIOCESE', 'organization.diocese.update.diocese'),
    ('ADMIN_DIOCESE', 'organization.deanery.read.diocese'),
    ('ADMIN_DIOCESE', 'organization.parish.read.diocese'),
    ('ADMIN_DIOCESE', 'organization.leader.read.diocese'),
    ('ADMIN_DEANERY', 'organization.deanery.read.deanery'),
    ('ADMIN_DEANERY', 'organization.deanery.update.deanery'),
    ('ADMIN_DEANERY', 'organization.parish.read.deanery'),
    ('ADMIN_DEANERY', 'organization.leader.read.deanery'),
    ('ADMIN_PARISH', 'organization.parish.read.parish'),
    ('ADMIN_PARISH', 'organization.parish.update.parish'),
    ('ADMIN_PARISH', 'organization.leader.read.parish'),
    ('ADMIN_PARISH', 'organization.leader.update.parish')
) AS grant_row(role_code, permission_code)
JOIN role r ON r.role_code = grant_row.role_code
JOIN permission p ON p.permission_code = grant_row.permission_code
ON CONFLICT (role_code, permission_code) DO UPDATE
SET
    effect = 'ALLOW',
    status = TRUE,
    expires_at = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
