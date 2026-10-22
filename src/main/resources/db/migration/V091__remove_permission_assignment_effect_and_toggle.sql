-- Bỏ toggle Cho phép/Từ chối ở phân quyền vai trò và tài khoản: chỉ còn phân quyền và thu hồi.
-- Bản ghi DENY không còn ý nghĩa (không còn cơ chế từ chối) nên xóa trước khi bỏ cột effect.
DELETE FROM role_permission WHERE effect = 'DENY';
DELETE FROM account_permission_override WHERE effect = 'DENY';

DROP VIEW IF EXISTS v_account_effective_permission;
DROP VIEW IF EXISTS v_role_permission_matrix;

ALTER TABLE role_permission DROP COLUMN IF EXISTS effect;
ALTER TABLE account_permission_override DROP COLUMN IF EXISTS effect;

CREATE VIEW v_role_permission_matrix AS
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
    rp.conditions,
    rp.expires_at,
    (
        rp.revoked_at IS NULL AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
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

CREATE VIEW v_account_effective_permission AS
SELECT
    ar.username,
    ar.role_code,
    p.permission_code,
    p.module_code,
    p.resource_code,
    p.action_code,
    p.scope_code,
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
  AND rp.revoked_at IS NULL
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
  AND apo.revoked_at IS NULL
  AND p.status = TRUE
  AND (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP);
