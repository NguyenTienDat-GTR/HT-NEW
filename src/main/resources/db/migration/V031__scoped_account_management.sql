INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('system.account.manage.diocese', 'Manage accounts in own diocese', 'system', 'account', 'manage', 'diocese', 'Create and manage lower-role accounts in own diocese', TRUE, 731, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account.manage.deanery', 'Manage accounts in own deanery', 'system', 'account', 'manage', 'deanery', 'Create and manage lower-role accounts in own deanery', TRUE, 732, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account.manage.parish', 'Manage accounts in own parish', 'system', 'account', 'manage', 'parish', 'Create and manage lower-role accounts in own parish', TRUE, 733, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_role.assign.parish', 'Assign scoped roles in own parish', 'system', 'account_role', 'assign', 'parish', 'Assign equal-or-lower roles in own parish', TRUE, 734, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_permission.manage.deanery', 'Manage account permissions in own deanery', 'system', 'account_permission', 'manage', 'deanery', 'Manage equal-or-lower scoped permissions in own deanery', TRUE, 735, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_permission.manage.parish', 'Manage account permissions in own parish', 'system', 'account_permission', 'manage', 'parish', 'Manage equal-or-lower scoped permissions in own parish', TRUE, 736, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO role_permission (
    id, role_code, permission_code, effect, assigned_by, assigned_at,
    status, created_at, created_by, updated_at, updated_by
)
SELECT
    (substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
     substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
     substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
     substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
     substr(md5(r.role_code || ':' || p.permission_code), 21, 12))::uuid,
    r.role_code, p.permission_code, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP,
    TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role r
JOIN permission p ON
    (r.role_code = 'ADMIN_DIOCESE' AND p.permission_code IN ('system.account.manage.diocese'))
 OR (r.role_code = 'ADMIN_DEANERY' AND p.permission_code IN ('system.account.manage.deanery', 'system.account_permission.manage.deanery'))
 OR (r.role_code = 'ADMIN_PARISH' AND p.permission_code IN ('system.account.manage.parish', 'system.account_role.assign.parish', 'system.account_permission.manage.parish'))
WHERE NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_code = r.role_code AND rp.permission_code = p.permission_code
);

-- Existing scoped assignment permissions remain the source of truth for diocese/deanery role assignment.
-- SUPER_ADMIN keeps system.account.manage.all but service-level policy restricts its targets to ADMIN_DIOCESE.
