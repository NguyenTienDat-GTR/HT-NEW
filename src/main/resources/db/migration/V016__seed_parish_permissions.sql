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
    'parish',
    'organization',
    'Parish',
    'Parish administration with diocese, deanery, and parish scope-aware access',
    30,
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
    ('organization.parish.create.all', 'Create parishes', 'organization', 'parish', 'create', 'all', 'Create parishes across the entire system', TRUE, 10, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.read.all', 'Read all parishes', 'organization', 'parish', 'read', 'all', 'Read any parish in the system', TRUE, 20, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.update.all', 'Update all parishes', 'organization', 'parish', 'update', 'all', 'Update any parish in the system', TRUE, 30, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.delete.all', 'Delete parishes', 'organization', 'parish', 'delete', 'all', 'Delete any parish in the system', TRUE, 40, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.create.diocese', 'Create parishes in own diocese', 'organization', 'parish', 'create', 'diocese', 'Create parishes inside the diocese resolved from the account leader hierarchy', TRUE, 50, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.read.diocese', 'Read parishes in own diocese', 'organization', 'parish', 'read', 'diocese', 'Read parishes inside the diocese resolved from the account leader hierarchy', TRUE, 60, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.update.diocese', 'Update parishes in own diocese', 'organization', 'parish', 'update', 'diocese', 'Update parishes inside the diocese resolved from the account leader hierarchy', TRUE, 70, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.delete.diocese', 'Delete parishes in own diocese', 'organization', 'parish', 'delete', 'diocese', 'Delete parishes inside the diocese resolved from the account leader hierarchy', TRUE, 80, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.create.deanery', 'Create parishes in own deanery', 'organization', 'parish', 'create', 'deanery', 'Create parishes inside the deanery resolved from the account leader hierarchy', TRUE, 90, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.read.deanery', 'Read parishes in own deanery', 'organization', 'parish', 'read', 'deanery', 'Read parishes inside the deanery resolved from the account leader hierarchy', TRUE, 100, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.update.deanery', 'Update parishes in own deanery', 'organization', 'parish', 'update', 'deanery', 'Update parishes inside the deanery resolved from the account leader hierarchy', TRUE, 110, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.delete.deanery', 'Delete parishes in own deanery', 'organization', 'parish', 'delete', 'deanery', 'Delete parishes inside the deanery resolved from the account leader hierarchy', TRUE, 120, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.read.parish', 'Read own parish', 'organization', 'parish', 'read', 'parish', 'Read the parish resolved from the account leader hierarchy', TRUE, 130, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.update.parish', 'Update own parish', 'organization', 'parish', 'update', 'parish', 'Update the parish resolved from the account leader hierarchy', TRUE, 140, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
    'organization.parish.create.all',
    'organization.parish.read.all',
    'organization.parish.update.all',
    'organization.parish.delete.all',
    'organization.parish.create.diocese',
    'organization.parish.read.diocese',
    'organization.parish.update.diocese',
    'organization.parish.delete.diocese',
    'organization.parish.create.deanery',
    'organization.parish.read.deanery',
    'organization.parish.update.deanery',
    'organization.parish.delete.deanery',
    'organization.parish.read.parish',
    'organization.parish.update.parish'
)
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = 'SUPER_ADMIN'
      AND rp.permission_code = p.permission_code
);
