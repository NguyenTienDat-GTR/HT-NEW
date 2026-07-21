WITH policy_row(http_method, path_pattern, handler_name, permission_code, scope_strategy) AS (
    VALUES
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.read.all', 'ALL'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.read.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.read.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.read.parish', 'PARISH'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.assign.all', 'ALL'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.assign.parish', 'PARISH'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.revoke.all', 'ALL'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.revoke.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.revoke.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions', 'RolePermissionController.findAll', 'system.role_permission.revoke.parish', 'PARISH'),

        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.read.all', 'ALL'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.read.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.read.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.read.parish', 'PARISH'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.assign.all', 'ALL'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.assign.parish', 'PARISH'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.revoke.all', 'ALL'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.revoke.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.revoke.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions/{id}', 'RolePermissionController.findById', 'system.role_permission.revoke.parish', 'PARISH'),

        ('POST', '/api/system/role-permissions', 'RolePermissionController.create', 'system.role_permission.assign.all', 'ALL'),
        ('POST', '/api/system/role-permissions', 'RolePermissionController.create', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('POST', '/api/system/role-permissions', 'RolePermissionController.create', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('POST', '/api/system/role-permissions', 'RolePermissionController.create', 'system.role_permission.assign.parish', 'PARISH'),
        ('POST', '/api/system/role-permissions/bulk', 'RolePermissionController.bulkAssign', 'system.role_permission.assign.all', 'ALL'),
        ('POST', '/api/system/role-permissions/bulk', 'RolePermissionController.bulkAssign', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('POST', '/api/system/role-permissions/bulk', 'RolePermissionController.bulkAssign', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('POST', '/api/system/role-permissions/bulk', 'RolePermissionController.bulkAssign', 'system.role_permission.assign.parish', 'PARISH'),
        ('PUT', '/api/system/role-permissions/{id}', 'RolePermissionController.update', 'system.role_permission.assign.all', 'ALL'),
        ('PUT', '/api/system/role-permissions/{id}', 'RolePermissionController.update', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('PUT', '/api/system/role-permissions/{id}', 'RolePermissionController.update', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('PUT', '/api/system/role-permissions/{id}', 'RolePermissionController.update', 'system.role_permission.assign.parish', 'PARISH'),

        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.assign.all', 'ALL'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.assign.parish', 'PARISH'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.revoke.all', 'ALL'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.revoke.diocese', 'DIOCESE'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.revoke.deanery', 'DEANERY'),
        ('PATCH', '/api/system/role-permissions/{id}/toggle-status', 'RolePermissionController.toggleStatus', 'system.role_permission.revoke.parish', 'PARISH'),

        ('GET', '/api/system/role-permissions/assignable-permissions', 'RolePermissionController.findAssignablePermissions', 'system.role_permission.assign.all', 'ALL'),
        ('GET', '/api/system/role-permissions/assignable-permissions', 'RolePermissionController.findAssignablePermissions', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('GET', '/api/system/role-permissions/assignable-permissions', 'RolePermissionController.findAssignablePermissions', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('GET', '/api/system/role-permissions/assignable-permissions', 'RolePermissionController.findAssignablePermissions', 'system.role_permission.assign.parish', 'PARISH'),

        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.read.all', 'ALL'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.read.diocese', 'DIOCESE'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.read.deanery', 'DEANERY'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.read.parish', 'PARISH'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.assign.all', 'ALL'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.assign.parish', 'PARISH'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.revoke.all', 'ALL'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.revoke.diocese', 'DIOCESE'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.revoke.deanery', 'DEANERY'),
        ('GET', '/api/system/permissions', 'PermissionController.findAll', 'system.role_permission.revoke.parish', 'PARISH'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.read.all', 'ALL'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.read.diocese', 'DIOCESE'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.read.deanery', 'DEANERY'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.read.parish', 'PARISH'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.assign.all', 'ALL'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.assign.diocese', 'DIOCESE'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.assign.deanery', 'DEANERY'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.assign.parish', 'PARISH'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.revoke.all', 'ALL'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.revoke.diocese', 'DIOCESE'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.revoke.deanery', 'DEANERY'),
        ('GET', '/api/system/permissions/{permissionCode}', 'PermissionController.findById', 'system.role_permission.revoke.parish', 'PARISH')
)
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
SELECT
    (
        substr(md5(pr.http_method || ':' || pr.path_pattern || ':' || pr.permission_code), 1, 8) || '-' ||
        substr(md5(pr.http_method || ':' || pr.path_pattern || ':' || pr.permission_code), 9, 4) || '-' ||
        substr(md5(pr.http_method || ':' || pr.path_pattern || ':' || pr.permission_code), 13, 4) || '-' ||
        substr(md5(pr.http_method || ':' || pr.path_pattern || ':' || pr.permission_code), 17, 4) || '-' ||
        substr(md5(pr.http_method || ':' || pr.path_pattern || ':' || pr.permission_code), 21, 12)
    )::uuid,
    pr.http_method,
    pr.path_pattern,
    pr.handler_name,
    pr.permission_code,
    pr.scope_strategy,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    TRUE
FROM policy_row pr
JOIN permission p ON p.permission_code = pr.permission_code
ON CONFLICT (http_method, path_pattern, permission_code) DO UPDATE
SET handler_name = EXCLUDED.handler_name,
    scope_strategy = EXCLUDED.scope_strategy,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
