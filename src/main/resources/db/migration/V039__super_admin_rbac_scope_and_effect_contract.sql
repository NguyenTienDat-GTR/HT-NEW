-- SUPER_ADMIN scope tightening and effect-based permission mapping contract.

UPDATE role_permission
SET status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE status IS DISTINCT FROM TRUE;

UPDATE account_permission_override
SET status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE status IS DISTINCT FROM TRUE;

WITH unwanted_permission AS (
    SELECT p.permission_code
    FROM permission p
    WHERE
        p.permission_code = 'organization.diocese.delete.all'
        OR p.permission_code LIKE 'organization.deanery.create.%'
        OR p.permission_code LIKE 'organization.deanery.update.%'
        OR p.permission_code LIKE 'organization.deanery.delete.%'
        OR p.permission_code LIKE 'organization.deanery.toggle.%'
        OR p.permission_code LIKE 'organization.parish.create.%'
        OR p.permission_code LIKE 'organization.parish.update.%'
        OR p.permission_code LIKE 'organization.parish.delete.%'
        OR p.permission_code LIKE 'organization.parish.toggle.%'
        OR p.permission_code LIKE 'organization.leader.%'
        OR p.permission_code LIKE 'organization.position.%'
        OR p.permission_code LIKE 'organization.executive_board%'
        OR p.permission_code LIKE 'training.%'
)
UPDATE role_permission rp
SET effect = 'DENY',
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM unwanted_permission up
WHERE rp.role_code = 'SUPER_ADMIN'
  AND rp.permission_code = up.permission_code;

WITH unwanted_permission AS (
    SELECT p.permission_code
    FROM permission p
    WHERE
        p.permission_code = 'organization.diocese.delete.all'
        OR p.permission_code LIKE 'organization.deanery.create.%'
        OR p.permission_code LIKE 'organization.deanery.update.%'
        OR p.permission_code LIKE 'organization.deanery.delete.%'
        OR p.permission_code LIKE 'organization.deanery.toggle.%'
        OR p.permission_code LIKE 'organization.parish.create.%'
        OR p.permission_code LIKE 'organization.parish.update.%'
        OR p.permission_code LIKE 'organization.parish.delete.%'
        OR p.permission_code LIKE 'organization.parish.toggle.%'
        OR p.permission_code LIKE 'organization.leader.%'
        OR p.permission_code LIKE 'organization.position.%'
        OR p.permission_code LIKE 'organization.executive_board%'
        OR p.permission_code LIKE 'training.%'
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
        substr(md5('SUPER_ADMIN_DENY:' || up.permission_code), 1, 8) || '-' ||
        substr(md5('SUPER_ADMIN_DENY:' || up.permission_code), 9, 4) || '-' ||
        substr(md5('SUPER_ADMIN_DENY:' || up.permission_code), 13, 4) || '-' ||
        substr(md5('SUPER_ADMIN_DENY:' || up.permission_code), 17, 4) || '-' ||
        substr(md5('SUPER_ADMIN_DENY:' || up.permission_code), 21, 12)
    )::uuid,
    'SUPER_ADMIN',
    up.permission_code,
    'DENY',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM unwanted_permission up
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = 'SUPER_ADMIN'
      AND rp.permission_code = up.permission_code
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
        rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP
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
  AND p.status = TRUE
  AND (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP);
