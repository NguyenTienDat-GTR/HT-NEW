-- API permission policies for option endpoints that return only unassigned roles/permissions.

INSERT INTO api_permission_policy (
    id,
    http_method,
    path_pattern,
    handler_name,
    permission_code,
    scope_strategy,
    is_system,
    created_at,
    created_by,
    updated_at,
    updated_by,
    status
)
VALUES
    ('4d6df1a4-d4c2-4206-8e2d-8a647c2d7201', 'GET', '/api/system/role-permissions/assignable-permissions', 'RolePermissionController.findAssignablePermissions', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('4d6df1a4-d4c2-4206-8e2d-8a647c2d7202', 'GET', '/api/system/role-permissions/assignable-permissions', 'RolePermissionController.findAssignablePermissions', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('4d6df1a4-d4c2-4206-8e2d-8a647c2d7203', 'GET', '/api/system/account-permissions/assignable-permissions', 'AccountPermissionOverrideController.findAssignablePermissions', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('4d6df1a4-d4c2-4206-8e2d-8a647c2d7204', 'GET', '/api/system/account-permissions/assignable-permissions', 'AccountPermissionOverrideController.findAssignablePermissions', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('4d6df1a4-d4c2-4206-8e2d-8a647c2d7205', 'GET', '/api/system/account-permissions/assignable-permissions', 'AccountPermissionOverrideController.findAssignablePermissions', 'system.account_permission.manage.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('4d6df1a4-d4c2-4206-8e2d-8a647c2d7206', 'GET', '/api/system/account-permissions/assignable-permissions', 'AccountPermissionOverrideController.findAssignablePermissions', 'system.account_permission.manage.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE)
ON CONFLICT (http_method, path_pattern, permission_code) DO NOTHING;
