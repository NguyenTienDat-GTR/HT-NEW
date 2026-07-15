-- Keep the account-role assignment screen visible for every admin actor that can manage it.
-- Existing databases may already have these rows with a disabled status or stale effect.

WITH grant_map(role_code, permission_code) AS (
    VALUES
        ('SUPER_ADMIN', 'system.account_role.assign.all'),
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
        ('SUPER_ADMIN', 'system.account_role.assign.all'),
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
