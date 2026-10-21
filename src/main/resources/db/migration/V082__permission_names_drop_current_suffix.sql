-- Bỏ chữ "hiện tại" ở cuối tên quyền (vd "Xem chức vụ - Giáo phận hiện tại" -> "Xem chức vụ - Giáo phận").
UPDATE permission
SET permission_name = regexp_replace(permission_name, '\s+hiện tại$', '', 'i')
WHERE permission_name ~* '\s+hiện tại$';
