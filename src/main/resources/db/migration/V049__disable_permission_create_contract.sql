UPDATE permission
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code = 'system.permission.create.all';

UPDATE role_permission
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code = 'system.permission.create.all';

UPDATE account_permission_override
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code = 'system.permission.create.all';
