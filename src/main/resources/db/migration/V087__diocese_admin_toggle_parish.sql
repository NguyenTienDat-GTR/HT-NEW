-- Admin giáo phận được bật/tắt giáo xứ trong giáo phận: đổi quyền toggle.diocese của giáo xứ từ từ chối sang cho phép.
UPDATE role_permission
SET effect = 'ALLOW', status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE role_code = 'ADMIN_DIOCESE' AND permission_code = 'organization.parish.toggle.diocese';

INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, effect, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, 'ADMIN_DIOCESE', 'organization.parish.toggle.diocese', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM role WHERE role_code = 'ADMIN_DIOCESE')
  AND EXISTS (SELECT 1 FROM permission WHERE permission_code = 'organization.parish.toggle.diocese')
  AND NOT EXISTS (SELECT 1 FROM role_permission WHERE role_code = 'ADMIN_DIOCESE' AND permission_code = 'organization.parish.toggle.diocese');
