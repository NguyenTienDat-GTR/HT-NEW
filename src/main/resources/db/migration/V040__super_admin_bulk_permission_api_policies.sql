-- API permission policies for SUPER_ADMIN scoped bulk assignment endpoints.

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
    ('b7d94f95-06a7-4f61-a101-4dd7f7695101', 'GET', '/api/system/accounts/permission-targets', 'AccountController.permissionTargets', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('b7d94f95-06a7-4f61-a101-4dd7f7695102', 'GET', '/api/system/accounts/permission-targets', 'AccountController.permissionTargets', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('b7d94f95-06a7-4f61-a101-4dd7f7695103', 'POST', '/api/system/role-permissions/bulk', 'RolePermissionController.bulkAssign', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('b7d94f95-06a7-4f61-a101-4dd7f7695104', 'POST', '/api/system/role-permissions/bulk', 'RolePermissionController.bulkAssign', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('b7d94f95-06a7-4f61-a101-4dd7f7695105', 'POST', '/api/system/account-permissions/bulk', 'AccountPermissionOverrideController.bulkAssign', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('b7d94f95-06a7-4f61-a101-4dd7f7695106', 'POST', '/api/system/account-permissions/bulk', 'AccountPermissionOverrideController.bulkAssign', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE)
ON CONFLICT (http_method, path_pattern, permission_code) DO NOTHING;
