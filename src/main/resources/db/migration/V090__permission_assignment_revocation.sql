-- Thu hồi quyền (hết hạn / thủ công) giữ lại bản ghi để hiển thị lịch sử; quyền đã thu hồi không bật lại được.
ALTER TABLE role_permission
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS revocation_reason VARCHAR(20);

ALTER TABLE account_permission_override
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS revocation_reason VARCHAR(20);

-- Bản ghi đã quá hạn từ trước: đánh dấu thu hồi do hết hạn tại đúng thời điểm hết hạn.
UPDATE role_permission
SET revoked_at = expires_at, revoked_by = 'system:permission-expiry-revocation', revocation_reason = 'EXPIRED'
WHERE revoked_at IS NULL AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP;

UPDATE account_permission_override
SET revoked_at = expires_at, revoked_by = 'system:permission-expiry-revocation', revocation_reason = 'EXPIRED'
WHERE revoked_at IS NULL AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP;

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
  AND apo.revoked_at IS NULL
  AND p.status = TRUE
  AND (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP);
