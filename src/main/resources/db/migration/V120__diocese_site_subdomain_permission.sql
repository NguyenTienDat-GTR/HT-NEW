-- Quyền cấp / thu hồi subdomain (trang riêng) cho giáo phận: chỉ SUPER_ADMIN.
INSERT INTO permission_action (
    action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('manage_site_subdomain', 'Manage site subdomain', 'Grant, change and revoke the subdomain (own site) of an organization unit', 95, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('organization.diocese.site_subdomain.manage.all', 'Quản lý subdomain giáo phận', 'organization', 'diocese', 'manage_site_subdomain', 'all',
     'Cấp, đổi và thu hồi subdomain (trang riêng) cho bất kỳ giáo phận nào', TRUE, 41, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET
    permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, r.role_code, p.permission_code, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'SYSTEM', CURRENT_TIMESTAMP
FROM role r
JOIN permission p ON p.permission_code = 'organization.diocese.site_subdomain.manage.all'
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_code, permission_code) DO UPDATE
SET expires_at = NULL, revoked_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
