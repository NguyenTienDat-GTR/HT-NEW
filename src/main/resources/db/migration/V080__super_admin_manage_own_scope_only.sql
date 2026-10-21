-- SUPER_ADMIN chỉ quản lý vai trò hệ thống / do super-admin tạo và tài khoản do super-admin tạo.
-- Gỡ các phân quyền mà super-admin đã gán trên vai trò và tài khoản thuộc phạm vi các cấp nhỏ hơn.
DELETE FROM role_permission rp
USING role r
WHERE r.role_code = rp.role_code
  AND COALESCE(r.is_system, FALSE) = FALSE
  AND lower(COALESCE(r.created_by, '')) NOT IN ('super-admin', 'system')
  AND (lower(COALESCE(rp.created_by, '')) = 'super-admin' OR lower(COALESCE(rp.assigned_by, '')) = 'super-admin');

DELETE FROM account_permission_override o
USING account a
WHERE a.username = o.username
  AND lower(COALESCE(a.created_by, '')) NOT IN ('super-admin', 'system')
  AND lower(COALESCE(o.created_by, '')) = 'super-admin';
