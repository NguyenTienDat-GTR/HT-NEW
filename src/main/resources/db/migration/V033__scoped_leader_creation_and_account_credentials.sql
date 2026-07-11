INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('organization.leader.create.diocese', 'Create leaders in own diocese', 'organization', 'leader', 'create', 'diocese', 'Create a leader in any parish inside the active diocese', TRUE, 737, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.leader.create.deanery', 'Create leaders in own deanery', 'organization', 'leader', 'create', 'deanery', 'Create a leader in any parish inside the active deanery', TRUE, 738, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
       (r.role_code = 'ADMIN_DIOCESE' AND p.permission_code = 'organization.leader.create.diocese')
    OR (r.role_code = 'ADMIN_DEANERY' AND p.permission_code = 'organization.leader.create.deanery')
    OR (r.role_code = 'ADMIN_PARISH' AND p.permission_code = 'organization.leader.create.parish')
WHERE NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_code = r.role_code AND rp.permission_code = p.permission_code
);

-- SUPER_ADMIN is deliberately excluded from leader creation, including tokens refreshed after this migration.
UPDATE role_permission
SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE role_code = 'SUPER_ADMIN'
  AND permission_code IN (
      'organization.leader.create.diocese',
      'organization.leader.create.deanery',
      'organization.leader.create.parish'
  );

-- Only one direct, leaderless account may exist for a diocese. Application policy additionally requires ADMIN_DIOCESE.
CREATE UNIQUE INDEX IF NOT EXISTS uk_account_one_leaderless_diocese
    ON account(diocese_id)
    WHERE leader_id IS NULL AND diocese_id IS NOT NULL;
