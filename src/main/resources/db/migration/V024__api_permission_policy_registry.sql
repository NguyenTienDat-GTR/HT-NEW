CREATE TABLE api_permission_policy
(
    id             UUID PRIMARY KEY,
    http_method    VARCHAR(10)              NOT NULL,
    path_pattern   VARCHAR(255)             NOT NULL,
    handler_name   VARCHAR(255),
    permission_code VARCHAR(255)            NOT NULL,
    scope_strategy VARCHAR(50)              NOT NULL,
    is_system      BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMP WITH TIME ZONE,
    updated_by     VARCHAR(100),
    status         BOOLEAN                  NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_api_permission_policy_permission FOREIGN KEY (permission_code) REFERENCES permission (permission_code),
    CONSTRAINT uk_api_permission_policy_route_permission UNIQUE (http_method, path_pattern, permission_code),
    CONSTRAINT chk_api_permission_policy_scope_strategy CHECK (scope_strategy IN ('NONE', 'ALL', 'DIOCESE', 'DEANERY', 'PARISH', 'OWN'))
);

CREATE INDEX idx_api_permission_policy_route
    ON api_permission_policy(http_method, path_pattern, status);

CREATE INDEX idx_api_permission_policy_permission
    ON api_permission_policy(permission_code, status);

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
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437001', 'GET', '/api/auth/admin/summary', 'AuthController.adminSummary', 'auth.session.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437002', 'POST', '/api/system/permissions', 'PermissionController.create', 'system.permission.create.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437003', 'GET', '/api/system/permissions', 'PermissionController.findAll', 'system.permission.read.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437004', 'GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.permission.read.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437005', 'PUT', '/api/system/permissions/{permissionCode}', 'PermissionController.update', 'system.permission.update.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437006', 'DELETE', '/api/system/permissions/{permissionCode}', 'PermissionController.delete', 'system.permission.delete.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437007', 'PATCH', '/api/system/permissions/{permissionCode}/toggle-status', 'PermissionController.toggleStatus', 'system.permission.update.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437008', 'POST', '/api/system/roles', 'RoleController.create', 'system.role.create.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437009', 'GET', '/api/system/roles', 'RoleController.findAll', 'system.role.read.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437010', 'GET', '/api/system/roles/{roleCode}', 'RoleController.findById', 'system.role.read.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437011', 'PUT', '/api/system/roles/{roleCode}', 'RoleController.update', 'system.role.update.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437012', 'DELETE', '/api/system/roles/{roleCode}', 'RoleController.delete', 'system.role.delete.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437013', 'PATCH', '/api/system/roles/{roleCode}/toggle-status', 'RoleController.toggleStatus', 'system.role.update.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437014', 'POST', '/api/system/role-permissions', 'RolePermissionController.create', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437015', 'POST', '/api/system/role-permissions', 'RolePermissionController.create', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437016', 'GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437017', 'GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437018', 'GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.revoke.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437019', 'GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.revoke.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437020', 'GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437021', 'GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437022', 'GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.revoke.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437023', 'GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.revoke.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437024', 'PUT', '/api/system/role-permissions/{id}', 'RolePermissionController.update', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437025', 'PUT', '/api/system/role-permissions/{id}', 'RolePermissionController.update', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437026', 'DELETE', '/api/system/role-permissions/{id}', 'RolePermissionController.delete', 'system.role_permission.revoke.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437027', 'DELETE', '/api/system/role-permissions/{id}', 'RolePermissionController.delete', 'system.role_permission.revoke.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437028', 'PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437029', 'PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437030', 'POST', '/api/system/account-permissions', 'AccountPermissionOverrideController.create', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437031', 'POST', '/api/system/account-permissions', 'AccountPermissionOverrideController.create', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437032', 'GET', '/api/system/account-permissions', 'AccountPermissionOverrideController.findAll', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437033', 'GET', '/api/system/account-permissions', 'AccountPermissionOverrideController.findAll', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437034', 'GET', '/api/system/account-permissions/{id}', 'AccountPermissionOverrideController.findById', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437035', 'GET', '/api/system/account-permissions/{id}', 'AccountPermissionOverrideController.findById', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437036', 'PUT', '/api/system/account-permissions/{id}', 'AccountPermissionOverrideController.update', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437037', 'PUT', '/api/system/account-permissions/{id}', 'AccountPermissionOverrideController.update', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437038', 'DELETE', '/api/system/account-permissions/{id}', 'AccountPermissionOverrideController.delete', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437039', 'DELETE', '/api/system/account-permissions/{id}', 'AccountPermissionOverrideController.delete', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437040', 'PATCH', '/api/system/account-permissions/{id}/toggle-status', 'AccountPermissionOverrideController.toggleStatus', 'system.account_permission.manage.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437041', 'PATCH', '/api/system/account-permissions/{id}/toggle-status', 'AccountPermissionOverrideController.toggleStatus', 'system.account_permission.manage.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437042', 'POST', '/api/system/account-roles', 'AccountRoleController.assignRole', 'system.account_role.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437043', 'POST', '/api/system/account-roles', 'AccountRoleController.assignRole', 'system.account_role.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d437044', 'POST', '/api/system/account-roles', 'AccountRoleController.assignRole', 'system.account_role.assign.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE)
ON CONFLICT (http_method, path_pattern, permission_code) DO NOTHING;
