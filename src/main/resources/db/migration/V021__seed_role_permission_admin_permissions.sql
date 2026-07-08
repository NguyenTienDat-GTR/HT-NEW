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
    ('role_permission', 'system', 'Role permission', 'Role-permission assignment management', 70, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('account_permission', 'system', 'Account permission', 'Direct account permission override management', 80, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (resource_code) DO UPDATE
SET
    module_code = EXCLUDED.module_code,
    resource_name = EXCLUDED.resource_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission_action (
    action_code,
    action_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES
    ('revoke', 'Revoke', 'Revoke permission assignment', 90, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
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
    ('system.role.create.all', 'Create roles', 'system', 'role', 'create', 'all', 'Create system roles', TRUE, 490, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.read.all', 'Read roles', 'system', 'role', 'read', 'all', 'Read system roles', TRUE, 500, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.update.all', 'Update roles', 'system', 'role', 'update', 'all', 'Update system roles', TRUE, 510, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.delete.all', 'Delete roles', 'system', 'role', 'delete', 'all', 'Delete system roles', TRUE, 520, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.permission.create.all', 'Create permissions', 'system', 'permission', 'create', 'all', 'Create system permissions', TRUE, 530, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.permission.read.all', 'Read permissions', 'system', 'permission', 'read', 'all', 'Read system permissions', TRUE, 540, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.permission.update.all', 'Update permissions', 'system', 'permission', 'update', 'all', 'Update system permissions', TRUE, 550, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.permission.delete.all', 'Delete permissions', 'system', 'permission', 'delete', 'all', 'Delete system permissions', TRUE, 560, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.assign.all', 'Assign permissions to any role', 'system', 'role_permission', 'assign', 'all', 'Assign permissions to any role in the system', TRUE, 570, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.revoke.all', 'Revoke permissions from any role', 'system', 'role_permission', 'revoke', 'all', 'Revoke permissions from any role in the system', TRUE, 580, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.assign.diocese', 'Assign scoped permissions in own diocese', 'system', 'role_permission', 'assign', 'diocese', 'Assign non-all permissions to manageable roles', TRUE, 590, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.revoke.diocese', 'Revoke scoped permissions in own diocese', 'system', 'role_permission', 'revoke', 'diocese', 'Revoke non-all permissions from manageable roles', TRUE, 600, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_permission.manage.all', 'Manage all account permission overrides', 'system', 'account_permission', 'manage', 'all', 'Manage direct account permission overrides system-wide', TRUE, 610, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_permission.manage.diocese', 'Manage account permission overrides in own diocese', 'system', 'account_permission', 'manage', 'diocese', 'Manage direct account permission overrides in own diocese', TRUE, 620, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
        substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 21, 12)
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
JOIN permission p ON (
    r.role_code = 'SUPER_ADMIN'
    OR (r.role_code = 'ADMIN_DIOCESE' AND p.permission_code IN (
        'system.role.create.all', 'system.role.read.all', 'system.role.update.all', 'system.role.delete.all',
        'system.permission.create.all', 'system.permission.read.all', 'system.permission.update.all', 'system.permission.delete.all',
        'system.role_permission.assign.diocese', 'system.role_permission.revoke.diocese',
        'system.account_permission.manage.diocese'
    ))
)
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_DIOCESE')
AND p.permission_code IN (
    'system.role.create.all', 'system.role.read.all', 'system.role.update.all', 'system.role.delete.all',
    'system.permission.create.all', 'system.permission.read.all', 'system.permission.update.all', 'system.permission.delete.all',
    'system.role_permission.assign.all', 'system.role_permission.revoke.all',
    'system.role_permission.assign.diocese', 'system.role_permission.revoke.diocese',
    'system.account_permission.manage.all', 'system.account_permission.manage.diocese'
)
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = r.role_code
      AND rp.permission_code = p.permission_code
);

CREATE OR REPLACE VIEW v_role_permission_matrix AS
SELECT
    r.role_code,
    r.role_name,
    pm.module_code,
    pm.module_name,
    pr.resource_code,
    pr.resource_name,
    pa.action_code,
    pa.action_name,
    ps.scope_code,
    ps.scope_name,
    p.permission_code,
    p.permission_name,
    rp.effect,
    rp.conditions,
    rp.expires_at,
    (
        rp.status = TRUE
            AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
    ) AS is_active
FROM role r
JOIN role_permission rp
    ON rp.role_code = r.role_code
JOIN permission p
    ON p.permission_code = rp.permission_code
JOIN permission_module pm
    ON pm.module_code = p.module_code
JOIN permission_resource pr
    ON pr.resource_code = p.resource_code
   AND pr.module_code = p.module_code
JOIN permission_action pa
    ON pa.action_code = p.action_code
JOIN permission_scope ps
    ON ps.scope_code = p.scope_code
WHERE r.status = TRUE
  AND rp.status = TRUE
  AND p.status = TRUE;

CREATE OR REPLACE VIEW v_account_effective_permission AS
SELECT
    ar.username,
    ar.role_code,
    p.permission_code,
    p.module_code,
    p.resource_code,
    p.action_code,
    p.scope_code,
    rp.effect,
    'ROLE' AS source_type,
    ar.expires_at AS account_role_expires_at,
    rp.expires_at AS permission_expires_at
FROM account_role ar
JOIN account a
    ON a.username = ar.username
JOIN role r
    ON r.role_code = ar.role_code
JOIN role_permission rp
    ON rp.role_code = ar.role_code
JOIN permission p
    ON p.permission_code = rp.permission_code
WHERE a.status = TRUE
  AND ar.status = TRUE
  AND r.status = TRUE
  AND rp.status = TRUE
  AND p.status = TRUE
  AND (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)

UNION ALL

SELECT
    apo.username,
    NULL,
    p.permission_code,
    p.module_code,
    p.resource_code,
    p.action_code,
    p.scope_code,
    apo.effect,
    'ACCOUNT_OVERRIDE',
    NULL,
    apo.expires_at
FROM account_permission_override apo
JOIN account a
    ON a.username = apo.username
JOIN permission p
    ON p.permission_code = apo.permission_code
WHERE a.status = TRUE
  AND apo.status = TRUE
  AND p.status = TRUE
  AND (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP);
