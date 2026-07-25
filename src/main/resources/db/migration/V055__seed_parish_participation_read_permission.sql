-- Allow parish administrators to read participation records for leaders in their own parish.

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
    'training.participation.read.parish',
    'Read training registrations in own parish',
    'training',
    'participation',
    'read',
    'parish',
    'Read training registration records for leaders in own parish',
    TRUE,
    175,
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
        substr(md5('ADMIN_PARISH:training.participation.read.parish'), 1, 8) || '-' ||
        substr(md5('ADMIN_PARISH:training.participation.read.parish'), 9, 4) || '-' ||
        substr(md5('ADMIN_PARISH:training.participation.read.parish'), 13, 4) || '-' ||
        substr(md5('ADMIN_PARISH:training.participation.read.parish'), 17, 4) || '-' ||
        substr(md5('ADMIN_PARISH:training.participation.read.parish'), 21, 12)
    )::uuid,
    'ADMIN_PARISH',
    'training.participation.read.parish',
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
WHERE EXISTS (SELECT 1 FROM role WHERE role_code = 'ADMIN_PARISH')
  AND EXISTS (SELECT 1 FROM permission WHERE permission_code = 'training.participation.read.parish')
ON CONFLICT (role_code, permission_code) DO UPDATE
SET
    effect = 'ALLOW',
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

DELETE FROM account_permission_override apo
WHERE apo.permission_code = 'training.participation.read.parish'
  AND apo.effect = 'DENY'
  AND EXISTS (
      SELECT 1
      FROM account_role ar
      WHERE ar.username = apo.username
        AND ar.role_code = 'ADMIN_PARISH'
        AND ar.status = TRUE
  );
