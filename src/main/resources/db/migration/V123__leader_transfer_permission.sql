-- Tách quyền chuyển huynh trưởng sang giáo xứ khác khỏi quyền chỉnh sửa huynh trưởng (phạm vi giáo phận và giáo hạt).
-- Vai trò đang có quyền chỉnh sửa ở phạm vi nào thì được gán quyền chuyển ở phạm vi đó để giữ nguyên hành vi hiện tại; có thể thu hồi riêng sau.
INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('transfer', 'Transfer', 'Chuyển một đối tượng sang đơn vị khác', 96, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET action_name = EXCLUDED.action_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('organization.leader.transfer.diocese', 'Chuyển huynh trưởng sang giáo xứ khác trong giáo phận', 'organization', 'leader', 'transfer', 'diocese', 'Cho phép đổi giáo xứ của huynh trưởng sang giáo xứ khác trong giáo phận được phân quyền', TRUE, 741, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.leader.transfer.deanery', 'Chuyển huynh trưởng sang giáo xứ khác trong giáo hạt', 'organization', 'leader', 'transfer', 'deanery', 'Cho phép đổi giáo xứ của huynh trưởng sang giáo xứ khác trong giáo hạt được phân quyền', TRUE, 742, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, rp.role_code, REPLACE(rp.permission_code, '.update.', '.transfer.'), CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'SYSTEM', CURRENT_TIMESTAMP
FROM role_permission rp
WHERE rp.permission_code IN ('organization.leader.update.diocese', 'organization.leader.update.deanery')
  AND rp.status = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM role_permission ex
      WHERE ex.role_code = rp.role_code AND ex.permission_code = REPLACE(rp.permission_code, '.update.', '.transfer.')
  );
