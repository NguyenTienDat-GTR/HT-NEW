-- Phân công ban điều hành chỉ được quản lý đúng cấp của role: xóa quyền phân công khác scope khỏi các role admin.
DELETE FROM role_permission
WHERE (role_code = 'ADMIN_DIOCESE' AND permission_code LIKE 'organization.executive_board_assignment.%' AND permission_code NOT LIKE '%.diocese')
   OR (role_code = 'ADMIN_DEANERY' AND permission_code LIKE 'organization.executive_board_assignment.%' AND permission_code NOT LIKE '%.deanery')
   OR (role_code = 'ADMIN_PARISH' AND permission_code LIKE 'organization.executive_board_assignment.%' AND permission_code NOT LIKE '%.parish')
   OR (role_code = 'LD_TRUONG' AND permission_code LIKE 'organization.executive_board_assignment.%' AND permission_code NOT LIKE '%.diocese');
