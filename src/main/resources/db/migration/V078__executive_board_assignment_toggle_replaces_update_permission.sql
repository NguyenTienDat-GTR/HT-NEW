-- Phân công ban điều hành không còn chỉnh sửa (giữ lịch sử nhiệm kì): thay quyền update bằng quyền toggle.
INSERT INTO permission (permission_code, module_code, resource_code, action_code, scope_code, created_at, created_by, updated_at, updated_by, status, permission_name, description, is_system, display_order)
SELECT replace(p.permission_code, '.update.', '.toggle.'),
       p.module_code,
       p.resource_code,
       'toggle',
       p.scope_code,
       CURRENT_TIMESTAMP,
       'SYSTEM',
       CURRENT_TIMESTAMP,
       'SYSTEM',
       TRUE,
       replace(p.permission_name, 'Cập nhật phân công', 'Đổi trạng thái phân công'),
       replace(p.description, 'cập nhật phân công', 'đổi trạng thái phân công'),
       TRUE,
       p.display_order
FROM permission p
WHERE p.permission_code LIKE 'organization.executive_board_assignment.update.%'
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, effect, assigned_by, assigned_at)
SELECT gen_random_uuid()::text,
       rp.role_code,
       replace(rp.permission_code, '.update.', '.toggle.'),
       CURRENT_TIMESTAMP,
       'SYSTEM',
       CURRENT_TIMESTAMP,
       'SYSTEM',
       rp.status,
       rp.effect,
       'SYSTEM',
       CURRENT_TIMESTAMP
FROM role_permission rp
WHERE rp.permission_code LIKE 'organization.executive_board_assignment.update.%'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission existing
      WHERE existing.role_code = rp.role_code
        AND existing.permission_code = replace(rp.permission_code, '.update.', '.toggle.')
  );

DELETE FROM account_permission_override WHERE permission_code LIKE 'organization.executive_board_assignment.update.%';
DELETE FROM role_permission WHERE permission_code LIKE 'organization.executive_board_assignment.update.%';
DELETE FROM permission WHERE permission_code LIKE 'organization.executive_board_assignment.update.%';
