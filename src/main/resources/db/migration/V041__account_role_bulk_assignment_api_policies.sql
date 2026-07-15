-- API permission policies for scoped account-role bulk assignment helpers.

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
    ('f2b7387b-2b38-46ef-8612-49360c2ef101', 'POST', '/api/system/account-roles/bulk', 'AccountRoleController.bulkAssign', 'system.account_role.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef102', 'POST', '/api/system/account-roles/bulk', 'AccountRoleController.bulkAssign', 'system.account_role.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef103', 'POST', '/api/system/account-roles/bulk', 'AccountRoleController.bulkAssign', 'system.account_role.assign.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef104', 'POST', '/api/system/account-roles/bulk', 'AccountRoleController.bulkAssign', 'system.account_role.assign.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef105', 'GET', '/api/system/account-roles/assignable-roles', 'AccountRoleController.findAssignableRoles', 'system.account_role.assign.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef106', 'GET', '/api/system/account-roles/assignable-roles', 'AccountRoleController.findAssignableRoles', 'system.account_role.assign.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef107', 'GET', '/api/system/account-roles/assignable-roles', 'AccountRoleController.findAssignableRoles', 'system.account_role.assign.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('f2b7387b-2b38-46ef-8612-49360c2ef108', 'GET', '/api/system/account-roles/assignable-roles', 'AccountRoleController.findAssignableRoles', 'system.account_role.assign.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE)
ON CONFLICT (http_method, path_pattern, permission_code) DO NOTHING;
