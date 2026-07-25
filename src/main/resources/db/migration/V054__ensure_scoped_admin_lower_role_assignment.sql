-- Scoped admins may assign business roles in their scope and lower admin roles:
-- ADMIN_DIOCESE -> ADMIN_DEANERY, ADMIN_PARISH
-- ADMIN_DEANERY -> ADMIN_PARISH
-- ADMIN_PARISH -> no admin system role delegation.
-- Keep the required account-role assignment grants active on existing databases.

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
    ('system.account_role.assign.diocese', 'Assign account roles in own diocese', 'system', 'account_role', 'assign', 'diocese', 'Assign in-scope business roles and lower admin roles in own diocese', TRUE, 470, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_role.assign.deanery', 'Assign account roles in own deanery', 'system', 'account_role', 'assign', 'deanery', 'Assign in-scope business roles and lower admin roles in own deanery', TRUE, 480, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_role.assign.parish', 'Assign account roles in own parish', 'system', 'account_role', 'assign', 'parish', 'Assign in-scope business roles in own parish', TRUE, 734, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

WITH grant_map(role_code, permission_code) AS (
    VALUES
        ('ADMIN_DIOCESE', 'system.account_role.assign.diocese'),
        ('ADMIN_DEANERY', 'system.account_role.assign.deanery'),
        ('ADMIN_PARISH', 'system.account_role.assign.parish')
)
UPDATE role_permission rp
SET effect = 'ALLOW',
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM grant_map gm
WHERE rp.role_code = gm.role_code
  AND rp.permission_code = gm.permission_code;

WITH grant_map(role_code, permission_code) AS (
    VALUES
        ('ADMIN_DIOCESE', 'system.account_role.assign.diocese'),
        ('ADMIN_DEANERY', 'system.account_role.assign.deanery'),
        ('ADMIN_PARISH', 'system.account_role.assign.parish')
)
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
        substr(md5(gm.role_code || ':' || gm.permission_code), 1, 8) || '-' ||
        substr(md5(gm.role_code || ':' || gm.permission_code), 9, 4) || '-' ||
        substr(md5(gm.role_code || ':' || gm.permission_code), 13, 4) || '-' ||
        substr(md5(gm.role_code || ':' || gm.permission_code), 17, 4) || '-' ||
        substr(md5(gm.role_code || ':' || gm.permission_code), 21, 12)
    )::uuid,
    gm.role_code,
    gm.permission_code,
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM grant_map gm
JOIN role r ON r.role_code = gm.role_code
JOIN permission p ON p.permission_code = gm.permission_code
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = gm.role_code
      AND rp.permission_code = gm.permission_code
);

DELETE FROM account_permission_override apo
USING account_role ar
WHERE apo.username = ar.username
  AND ar.status = TRUE
  AND (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND apo.effect = 'DENY'
  AND (
      (ar.role_code = 'ADMIN_DIOCESE' AND apo.permission_code = 'system.account_role.assign.diocese')
      OR (ar.role_code = 'ADMIN_DEANERY' AND apo.permission_code = 'system.account_role.assign.deanery')
      OR (ar.role_code = 'ADMIN_PARISH' AND apo.permission_code = 'system.account_role.assign.parish')
  );
