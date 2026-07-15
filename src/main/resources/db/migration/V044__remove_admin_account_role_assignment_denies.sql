-- Scoped admin accounts must keep their account-role assignment authority.
-- Remove stale direct DENY overrides that hide the "Gán vai trò" menu and block the API.

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
