-- Chức vụ chỉ được quản lý đúng cấp của role: xóa quyền chức vụ khác scope khỏi các role admin.
DELETE FROM role_permission
WHERE (role_code = 'ADMIN_DIOCESE' AND permission_code LIKE 'organization.position.%' AND permission_code NOT LIKE '%.diocese')
   OR (role_code = 'ADMIN_DEANERY' AND permission_code LIKE 'organization.position.%' AND permission_code NOT LIKE '%.deanery')
   OR (role_code = 'ADMIN_PARISH' AND permission_code LIKE 'organization.position.%' AND permission_code NOT LIKE '%.parish')
   -- Trưởng ban cấp giáo phận: chỉ giữ quyền chức vụ cấp giáo phận.
   OR (role_code = 'LD_TRUONG' AND permission_code LIKE 'organization.position.%' AND permission_code NOT LIKE '%.diocese');
