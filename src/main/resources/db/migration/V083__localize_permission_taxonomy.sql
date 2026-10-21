-- Việt hóa nhãn nhóm quyền còn tiếng Anh và thống nhất cách gọi "phân quyền tài khoản", bỏ chữ "hiện tại" ở tên phạm vi.
UPDATE permission_resource SET resource_name = 'Cấu hình chứng nhận' WHERE resource_code = 'certificate_config' AND resource_name = 'Certificate configuration';
UPDATE permission_resource SET resource_name = 'Cấp độ huynh trưởng' WHERE resource_code = 'leader_level' AND resource_name = 'Leader level';
UPDATE permission_resource
SET resource_name = 'Phân quyền tài khoản',
    description = 'Phân quyền cho phép hoặc từ chối trực tiếp trên từng tài khoản.'
WHERE resource_code = 'account_permission';

UPDATE permission
SET permission_name = replace(permission_name, 'Quản lý quyền riêng tài khoản', 'Quản lý phân quyền tài khoản'),
    description = replace(description, 'quản lý quyền cho phép hoặc từ chối trực tiếp trên tài khoản', 'quản lý phân quyền cho phép hoặc từ chối trực tiếp trên tài khoản')
WHERE resource_code = 'account_permission';

UPDATE permission_scope SET scope_name = regexp_replace(scope_name, '\s+hiện tại$', '', 'i') WHERE scope_name ~* '\s+hiện tại$';
