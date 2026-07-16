INSERT INTO permission_action (
    action_code,
    action_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES (
    'toggle',
    'Toggle status',
    'Enable or disable an existing record',
    35,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (action_code) DO UPDATE
SET
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code,
    permission_name,
    module_code,
    resource_code,
    action_code,
    scope_code,
    description,
    is_system,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES
    ('system.role.create.all', 'Create roles system-wide', 'system', 'role', 'create', 'all', 'Create roles system-wide', TRUE, 490, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.read.all', 'Read roles system-wide', 'system', 'role', 'read', 'all', 'Read roles system-wide', TRUE, 491, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.update.all', 'Update roles system-wide', 'system', 'role', 'update', 'all', 'Update roles system-wide', TRUE, 492, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.toggle.all', 'Toggle roles system-wide', 'system', 'role', 'toggle', 'all', 'Enable or disable roles system-wide', TRUE, 493, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.create.diocese', 'Create roles in own diocese scope', 'system', 'role', 'create', 'diocese', 'Create manageable roles for diocese administrators', TRUE, 494, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.read.diocese', 'Read roles in own diocese scope', 'system', 'role', 'read', 'diocese', 'Read manageable roles for diocese administrators', TRUE, 495, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.update.diocese', 'Update roles in own diocese scope', 'system', 'role', 'update', 'diocese', 'Update manageable roles for diocese administrators', TRUE, 496, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.toggle.diocese', 'Toggle roles in own diocese scope', 'system', 'role', 'toggle', 'diocese', 'Enable or disable manageable roles for diocese administrators', TRUE, 497, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.create.deanery', 'Create roles in own deanery scope', 'system', 'role', 'create', 'deanery', 'Create manageable roles for deanery administrators', TRUE, 498, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.read.deanery', 'Read roles in own deanery scope', 'system', 'role', 'read', 'deanery', 'Read manageable roles for deanery administrators', TRUE, 499, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.update.deanery', 'Update roles in own deanery scope', 'system', 'role', 'update', 'deanery', 'Update manageable roles for deanery administrators', TRUE, 500, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.toggle.deanery', 'Toggle roles in own deanery scope', 'system', 'role', 'toggle', 'deanery', 'Enable or disable manageable roles for deanery administrators', TRUE, 501, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.create.parish', 'Create roles in own parish scope', 'system', 'role', 'create', 'parish', 'Create manageable roles for parish administrators', TRUE, 502, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.read.parish', 'Read roles in own parish scope', 'system', 'role', 'read', 'parish', 'Read manageable roles for parish administrators', TRUE, 503, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.update.parish', 'Update roles in own parish scope', 'system', 'role', 'update', 'parish', 'Update manageable roles for parish administrators', TRUE, 504, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role.toggle.parish', 'Toggle roles in own parish scope', 'system', 'role', 'toggle', 'parish', 'Enable or disable manageable roles for parish administrators', TRUE, 505, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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

UPDATE role_permission
SET
    status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE role_code IN ('ADMIN_DIOCESE', 'ADMIN_DEANERY', 'ADMIN_PARISH')
  AND permission_code IN (
      'system.role.create.all',
      'system.role.read.all',
      'system.role.update.all',
      'system.role.delete.all'
  );

UPDATE role_permission
SET
    status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code = 'system.role.delete.all';

UPDATE permission
SET
    status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code = 'system.role.delete.all';

INSERT INTO role_permission (
    id,
    role_code,
    permission_code,
    effect,
    assigned_by,
    assigned_at,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    (
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 1, 8) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 9, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 13, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 17, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 21, 12)
    )::uuid,
    grant_row.role_code,
    grant_row.permission_code,
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM (VALUES
    ('SUPER_ADMIN', 'system.role.create.all'),
    ('SUPER_ADMIN', 'system.role.read.all'),
    ('SUPER_ADMIN', 'system.role.update.all'),
    ('SUPER_ADMIN', 'system.role.toggle.all'),
    ('ADMIN_DIOCESE', 'system.role.create.diocese'),
    ('ADMIN_DIOCESE', 'system.role.read.diocese'),
    ('ADMIN_DIOCESE', 'system.role.update.diocese'),
    ('ADMIN_DIOCESE', 'system.role.toggle.diocese'),
    ('ADMIN_DEANERY', 'system.role.create.deanery'),
    ('ADMIN_DEANERY', 'system.role.read.deanery'),
    ('ADMIN_DEANERY', 'system.role.update.deanery'),
    ('ADMIN_DEANERY', 'system.role.toggle.deanery'),
    ('ADMIN_PARISH', 'system.role.create.parish'),
    ('ADMIN_PARISH', 'system.role.read.parish'),
    ('ADMIN_PARISH', 'system.role.update.parish'),
    ('ADMIN_PARISH', 'system.role.toggle.parish')
) AS grant_row(role_code, permission_code)
JOIN role r ON r.role_code = grant_row.role_code
JOIN permission p ON p.permission_code = grant_row.permission_code
ON CONFLICT (role_code, permission_code) DO UPDATE
SET
    effect = 'ALLOW',
    status = TRUE,
    expires_at = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

UPDATE api_permission_policy
SET
    permission_code = 'system.role.toggle.all',
    handler_name = 'RoleController.toggleStatus',
    scope_strategy = 'ALL',
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE http_method = 'PATCH'
  AND path_pattern = '/api/system/roles/{roleCode}/toggle-status'
  AND permission_code = 'system.role.update.all';

UPDATE api_permission_policy
SET
    status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE http_method = 'DELETE'
  AND path_pattern = '/api/system/roles/{roleCode}';

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
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450101', 'POST', '/api/system/roles', 'RoleController.create', 'system.role.create.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450102', 'POST', '/api/system/roles', 'RoleController.create', 'system.role.create.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450103', 'POST', '/api/system/roles', 'RoleController.create', 'system.role.create.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450104', 'GET', '/api/system/roles', 'RoleController.findAll', 'system.role.read.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450105', 'GET', '/api/system/roles', 'RoleController.findAll', 'system.role.read.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450106', 'GET', '/api/system/roles', 'RoleController.findAll', 'system.role.read.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450107', 'GET', '/api/system/roles/{roleCode}', 'RoleController.findById', 'system.role.read.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450108', 'GET', '/api/system/roles/{roleCode}', 'RoleController.findById', 'system.role.read.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450109', 'GET', '/api/system/roles/{roleCode}', 'RoleController.findById', 'system.role.read.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450110', 'PUT', '/api/system/roles/{roleCode}', 'RoleController.update', 'system.role.update.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450111', 'PUT', '/api/system/roles/{roleCode}', 'RoleController.update', 'system.role.update.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450112', 'PUT', '/api/system/roles/{roleCode}', 'RoleController.update', 'system.role.update.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450113', 'PATCH', '/api/system/roles/{roleCode}/toggle-status', 'RoleController.toggleStatus', 'system.role.toggle.all', 'ALL', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450114', 'PATCH', '/api/system/roles/{roleCode}/toggle-status', 'RoleController.toggleStatus', 'system.role.toggle.diocese', 'DIOCESE', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450115', 'PATCH', '/api/system/roles/{roleCode}/toggle-status', 'RoleController.toggleStatus', 'system.role.toggle.deanery', 'DEANERY', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE),
    ('7d2bc9d0-e0c9-4f1d-a101-778a0d450116', 'PATCH', '/api/system/roles/{roleCode}/toggle-status', 'RoleController.toggleStatus', 'system.role.toggle.parish', 'PARISH', TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE)
ON CONFLICT (http_method, path_pattern, permission_code) DO UPDATE
SET
    handler_name = EXCLUDED.handler_name,
    scope_strategy = EXCLUDED.scope_strategy,
    is_system = TRUE,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
