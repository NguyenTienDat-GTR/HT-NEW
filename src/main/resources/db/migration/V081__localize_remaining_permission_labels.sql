-- Việt hóa các tên / mô tả quyền còn bằng tiếng Anh để màn hình phân quyền hiển thị đồng nhất.
UPDATE permission AS p
SET permission_name = v.permission_name,
    description = v.description
FROM (VALUES
    ('organization.deanery.toggle.diocese', 'Đổi trạng thái giáo hạt - Giáo phận hiện tại', 'Cho phép bật hoặc tắt các giáo hạt trực thuộc giáo phận gắn với tài khoản hiện tại.'),
    ('organization.parish.toggle.deanery', 'Đổi trạng thái giáo xứ - Giáo hạt hiện tại', 'Cho phép bật hoặc tắt các giáo xứ trực thuộc giáo hạt gắn với tài khoản hiện tại.'),
    ('system.account_role.assign.diocese', 'Gán vai trò cho tài khoản - Giáo phận hiện tại', 'Cho phép gán vai trò nghiệp vụ và vai trò quản trị cấp thấp hơn trong giáo phận gắn với tài khoản hiện tại.'),
    ('system.account_role.assign.deanery', 'Gán vai trò cho tài khoản - Giáo hạt hiện tại', 'Cho phép gán vai trò nghiệp vụ và vai trò quản trị cấp thấp hơn trong giáo hạt gắn với tài khoản hiện tại.'),
    ('system.account_role.assign.parish', 'Gán vai trò cho tài khoản - Giáo xứ hiện tại', 'Cho phép gán vai trò nghiệp vụ trong giáo xứ gắn với tài khoản hiện tại.'),
    ('training.participation.read.parish', 'Xem hồ sơ tham dự khóa - Giáo xứ hiện tại', 'Cho phép xem hồ sơ đăng ký tham dự khóa của huynh trưởng trong giáo xứ gắn với tài khoản hiện tại.'),
    ('organization.diocese.toggle.all', 'Đổi trạng thái giáo phận - Toàn hệ thống', 'Cho phép bật hoặc tắt bất kỳ giáo phận nào trong hệ thống.')
) AS v(permission_code, permission_name, description)
WHERE p.permission_code = v.permission_code;

UPDATE role SET role_name = 'Quản trị tối cao', description = 'Quản trị viên toàn hệ thống, quản lý các vai trò hệ thống và tài khoản do super-admin tạo'
WHERE role_code = 'SUPER_ADMIN' AND role_name = 'Super Administrator';
