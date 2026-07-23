-- Align organization-unit toggle permissions with the current role contract:
-- SUPER_ADMIN toggles dioceses, ADMIN_DIOCESE toggles deaneries,
-- and ADMIN_DEANERY toggles parishes.

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
    ('organization.diocese.toggle.all', 'Toggle dioceses', 'organization', 'diocese', 'toggle', 'all', 'Enable or disable any diocese in the system', TRUE, 40, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.deanery.toggle.diocese', 'Toggle deaneries in own diocese', 'organization', 'deanery', 'toggle', 'diocese', 'Enable or disable direct-child deaneries inside the active diocese', TRUE, 735, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.parish.toggle.deanery', 'Toggle parishes in own deanery', 'organization', 'parish', 'toggle', 'deanery', 'Enable or disable direct-child parishes inside the active deanery', TRUE, 736, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET
    permission_name = EXCLUDED.permission_name,
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
    ('SUPER_ADMIN', 'organization.diocese.toggle.all'),
    ('ADMIN_DIOCESE', 'organization.deanery.toggle.diocese'),
    ('ADMIN_DEANERY', 'organization.parish.toggle.deanery')
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

UPDATE role_permission rp
SET
    effect = 'DENY',
    status = TRUE,
    expires_at = NULL,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM permission p
WHERE p.permission_code = rp.permission_code
  AND (
      (
          rp.role_code = 'SUPER_ADMIN'
          AND (
              p.permission_code LIKE 'organization.deanery.toggle.%'
              OR p.permission_code LIKE 'organization.parish.toggle.%'
          )
      )
      OR (
          rp.role_code = 'ADMIN_DIOCESE'
          AND (
              p.permission_code LIKE 'organization.diocese.toggle.%'
              OR p.permission_code LIKE 'organization.parish.toggle.%'
          )
      )
      OR (
          rp.role_code = 'ADMIN_DEANERY'
          AND (
              p.permission_code LIKE 'organization.diocese.toggle.%'
              OR p.permission_code LIKE 'organization.deanery.toggle.%'
          )
      )
      OR (
          rp.role_code = 'ADMIN_PARISH'
          AND p.permission_code LIKE 'organization.%.toggle.%'
      )
  );
