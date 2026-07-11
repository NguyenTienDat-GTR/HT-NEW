-- ADMIN_DIOCESE may now link to a leader, so the old leaderless-only index no longer represents the invariant.
DROP INDEX IF EXISTS uk_account_one_leaderless_diocese;

-- SUPER_ADMIN must create ADMIN_DIOCESE accounts through /api/system/accounts so credentials cannot be bypassed.
UPDATE role_permission
SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE role_code = 'SUPER_ADMIN'
  AND permission_code LIKE 'system.account_role.assign.%';
