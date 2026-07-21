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
VALUES ('toggle', 'Toggle status', 'Enable or disable an existing record', 35, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

WITH delete_permission AS (
    SELECT *
    FROM permission
    WHERE action_code = 'delete'
       OR permission_code LIKE '%.delete.%'
)
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
SELECT
    replace(permission_code, '.delete.', '.toggle.'),
    replace(replace(permission_name, 'Delete', 'Toggle'), 'Xóa', 'Đổi trạng thái'),
    module_code,
    resource_code,
    'toggle',
    scope_code,
    replace(replace(description, 'Delete', 'Toggle'), 'xóa', 'đổi trạng thái'),
    is_system,
    display_order,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM delete_permission
WHERE permission_code LIKE '%.delete.%'
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = 'toggle',
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
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
    ('system.role_permission.read.all', 'Read role permissions system-wide', 'system', 'role_permission', 'read', 'all', 'Read role permission assignments system-wide', TRUE, 565, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.read.diocese', 'Read role permissions in own diocese', 'system', 'role_permission', 'read', 'diocese', 'Read manageable role permission assignments in own diocese scope', TRUE, 595, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.read.deanery', 'Read role permissions in own deanery', 'system', 'role_permission', 'read', 'deanery', 'Read manageable role permission assignments in own deanery scope', TRUE, 596, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.read.parish', 'Read role permissions in own parish', 'system', 'role_permission', 'read', 'parish', 'Read manageable role permission assignments in own parish scope', TRUE, 597, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.assign.deanery', 'Assign scoped permissions in own deanery', 'system', 'role_permission', 'assign', 'deanery', 'Assign equal-or-lower scoped permissions to manageable roles', TRUE, 601, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.revoke.deanery', 'Revoke scoped permissions in own deanery', 'system', 'role_permission', 'revoke', 'deanery', 'Revoke equal-or-lower scoped permissions from manageable roles', TRUE, 602, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.assign.parish', 'Assign scoped permissions in own parish', 'system', 'role_permission', 'assign', 'parish', 'Assign equal-or-lower scoped permissions to manageable roles', TRUE, 603, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.role_permission.revoke.parish', 'Revoke scoped permissions in own parish', 'system', 'role_permission', 'revoke', 'parish', 'Revoke equal-or-lower scoped permissions from manageable roles', TRUE, 604, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = TRUE,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

WITH permission_mapping AS (
    SELECT
        old.permission_code AS delete_permission_code,
        replace(old.permission_code, '.delete.', '.toggle.') AS toggle_permission_code
    FROM permission old
    WHERE old.permission_code LIKE '%.delete.%'
)
INSERT INTO role_permission (
    id,
    role_code,
    permission_code,
    effect,
    conditions,
    assigned_by,
    assigned_at,
    expires_at,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    (
        substr(md5(rp.role_code || ':' || pm.toggle_permission_code), 1, 8) || '-' ||
        substr(md5(rp.role_code || ':' || pm.toggle_permission_code), 9, 4) || '-' ||
        substr(md5(rp.role_code || ':' || pm.toggle_permission_code), 13, 4) || '-' ||
        substr(md5(rp.role_code || ':' || pm.toggle_permission_code), 17, 4) || '-' ||
        substr(md5(rp.role_code || ':' || pm.toggle_permission_code), 21, 12)
    )::uuid,
    rp.role_code,
    pm.toggle_permission_code,
    rp.effect,
    rp.conditions,
    COALESCE(rp.assigned_by, 'SYSTEM'),
    COALESCE(rp.assigned_at, CURRENT_TIMESTAMP),
    rp.expires_at,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM role_permission rp
JOIN permission_mapping pm ON pm.delete_permission_code = rp.permission_code
JOIN permission p ON p.permission_code = pm.toggle_permission_code
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = EXCLUDED.effect,
    conditions = EXCLUDED.conditions,
    assigned_by = EXCLUDED.assigned_by,
    assigned_at = EXCLUDED.assigned_at,
    expires_at = EXCLUDED.expires_at,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

WITH permission_mapping AS (
    SELECT
        old.permission_code AS delete_permission_code,
        replace(old.permission_code, '.delete.', '.toggle.') AS toggle_permission_code
    FROM permission old
    WHERE old.permission_code LIKE '%.delete.%'
)
INSERT INTO account_permission_override (
    id,
    username,
    permission_code,
    effect,
    reason,
    expires_at,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    (
        substr(md5(apo.username || ':' || pm.toggle_permission_code), 1, 8) || '-' ||
        substr(md5(apo.username || ':' || pm.toggle_permission_code), 9, 4) || '-' ||
        substr(md5(apo.username || ':' || pm.toggle_permission_code), 13, 4) || '-' ||
        substr(md5(apo.username || ':' || pm.toggle_permission_code), 17, 4) || '-' ||
        substr(md5(apo.username || ':' || pm.toggle_permission_code), 21, 12)
    )::uuid,
    apo.username,
    pm.toggle_permission_code,
    apo.effect,
    apo.reason,
    apo.expires_at,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM account_permission_override apo
JOIN permission_mapping pm ON pm.delete_permission_code = apo.permission_code
JOIN permission p ON p.permission_code = pm.toggle_permission_code
ON CONFLICT (username, permission_code) DO UPDATE
SET effect = EXCLUDED.effect,
    reason = EXCLUDED.reason,
    expires_at = EXCLUDED.expires_at,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

WITH grant_row(role_code, permission_code) AS (
    VALUES
        ('SUPER_ADMIN', 'system.role_permission.read.all'),
        ('SUPER_ADMIN', 'system.role_permission.assign.all'),
        ('SUPER_ADMIN', 'system.role_permission.revoke.all'),
        ('ADMIN_DIOCESE', 'system.role_permission.read.diocese'),
        ('ADMIN_DIOCESE', 'system.role_permission.assign.diocese'),
        ('ADMIN_DIOCESE', 'system.role_permission.revoke.diocese'),
        ('ADMIN_DEANERY', 'system.role_permission.read.deanery'),
        ('ADMIN_DEANERY', 'system.role_permission.assign.deanery'),
        ('ADMIN_DEANERY', 'system.role_permission.revoke.deanery'),
        ('ADMIN_PARISH', 'system.role_permission.read.parish'),
        ('ADMIN_PARISH', 'system.role_permission.assign.parish'),
        ('ADMIN_PARISH', 'system.role_permission.revoke.parish')
)
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
FROM grant_row
JOIN role r ON r.role_code = grant_row.role_code
JOIN permission p ON p.permission_code = grant_row.permission_code
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW',
    expires_at = NULL,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

UPDATE role_permission
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code LIKE '%.delete.%';

UPDATE account_permission_override
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE permission_code LIKE '%.delete.%';

UPDATE api_permission_policy
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE http_method = 'DELETE'
   OR permission_code LIKE '%.delete.%';

WITH permission_mapping AS (
    SELECT
        old.permission_code AS delete_permission_code,
        replace(old.permission_code, '.delete.', '.toggle.') AS toggle_permission_code
    FROM permission old
    WHERE old.permission_code LIKE '%.delete.%'
),
delete_policy AS (
    SELECT
        app.path_pattern,
        app.scope_strategy,
        pm.toggle_permission_code
    FROM api_permission_policy app
    JOIN permission_mapping pm ON pm.delete_permission_code = app.permission_code
    WHERE app.http_method = 'DELETE'
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
        substr(md5('PATCH:' || dp.path_pattern || '/toggle-status:' || dp.toggle_permission_code), 1, 8) || '-' ||
        substr(md5('PATCH:' || dp.path_pattern || '/toggle-status:' || dp.toggle_permission_code), 9, 4) || '-' ||
        substr(md5('PATCH:' || dp.path_pattern || '/toggle-status:' || dp.toggle_permission_code), 13, 4) || '-' ||
        substr(md5('PATCH:' || dp.path_pattern || '/toggle-status:' || dp.toggle_permission_code), 17, 4) || '-' ||
        substr(md5('PATCH:' || dp.path_pattern || '/toggle-status:' || dp.toggle_permission_code), 21, 12)
    )::uuid,
    'PATCH',
    dp.path_pattern || '/toggle-status',
    NULL,
    dp.toggle_permission_code,
    dp.scope_strategy,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    TRUE
FROM delete_policy dp
JOIN permission p ON p.permission_code = dp.toggle_permission_code
ON CONFLICT (http_method, path_pattern, permission_code) DO UPDATE
SET status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

WITH update_policy AS (
    SELECT
        app.path_pattern,
        app.handler_name,
        replace(app.permission_code, '.update.', '.toggle.') AS toggle_permission_code,
        app.scope_strategy
    FROM api_permission_policy app
    WHERE app.http_method = 'PATCH'
      AND app.path_pattern LIKE '%/toggle-status'
      AND app.permission_code LIKE '%.update.%'
      AND EXISTS (
          SELECT 1
          FROM permission p
          WHERE p.permission_code = replace(app.permission_code, '.update.', '.toggle.')
      )
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
        substr(md5('PATCH_UPDATE_TO_TOGGLE:' || up.path_pattern || ':' || up.toggle_permission_code), 1, 8) || '-' ||
        substr(md5('PATCH_UPDATE_TO_TOGGLE:' || up.path_pattern || ':' || up.toggle_permission_code), 9, 4) || '-' ||
        substr(md5('PATCH_UPDATE_TO_TOGGLE:' || up.path_pattern || ':' || up.toggle_permission_code), 13, 4) || '-' ||
        substr(md5('PATCH_UPDATE_TO_TOGGLE:' || up.path_pattern || ':' || up.toggle_permission_code), 17, 4) || '-' ||
        substr(md5('PATCH_UPDATE_TO_TOGGLE:' || up.path_pattern || ':' || up.toggle_permission_code), 21, 12)
    )::uuid,
    'PATCH',
    up.path_pattern,
    up.handler_name,
    up.toggle_permission_code,
    up.scope_strategy,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    TRUE
FROM update_policy up
JOIN permission p ON p.permission_code = up.toggle_permission_code
ON CONFLICT (http_method, path_pattern, permission_code) DO UPDATE
SET handler_name = EXCLUDED.handler_name,
    scope_strategy = EXCLUDED.scope_strategy,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

UPDATE api_permission_policy app
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE app.http_method = 'PATCH'
  AND app.path_pattern LIKE '%/toggle-status'
  AND app.permission_code LIKE '%.update.%'
  AND EXISTS (
      SELECT 1
      FROM permission p
      WHERE p.permission_code = replace(app.permission_code, '.update.', '.toggle.')
  );

UPDATE permission
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE action_code = 'delete'
   OR permission_code LIKE '%.delete.%';
