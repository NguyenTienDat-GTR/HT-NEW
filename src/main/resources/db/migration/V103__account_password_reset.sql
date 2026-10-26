-- ============================================================================
-- V103: Admin đặt lại mật khẩu tài khoản.
--       - account.temporary_password_expires_at: hạn dùng của mật khẩu tạm do admin đặt lại (NULL = không có hạn).
--       - Quyền system.account.reset_password.{all,diocese,deanery,parish}, mặc định gán cho mọi vai trò đang có
--         quyền system.account.manage.<cùng phạm vi>; có thể cấp riêng cho vai trò / tài khoản khác.
-- ============================================================================

ALTER TABLE account
    ADD COLUMN IF NOT EXISTS temporary_password_expires_at TIMESTAMP WITH TIME ZONE NULL;

INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('reset_password', 'Đặt lại mật khẩu', 'Đặt lại mật khẩu tạm thời và gửi qua email', 70, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE SET status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('system.account.reset_password.all', 'Đặt lại mật khẩu tài khoản - Toàn hệ thống', 'system', 'account', 'reset_password', 'all',
     'Đặt lại mật khẩu tạm thời cho tài khoản do super-admin quản lý', TRUE, 741, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account.reset_password.diocese', 'Đặt lại mật khẩu tài khoản - Giáo phận', 'system', 'account', 'reset_password', 'diocese',
     'Đặt lại mật khẩu tạm thời cho tài khoản cấp thấp hơn trong giáo phận của mình', TRUE, 742, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account.reset_password.deanery', 'Đặt lại mật khẩu tài khoản - Giáo hạt', 'system', 'account', 'reset_password', 'deanery',
     'Đặt lại mật khẩu tạm thời cho tài khoản cấp thấp hơn trong giáo hạt của mình', TRUE, 743, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account.reset_password.parish', 'Đặt lại mật khẩu tài khoản - Giáo xứ', 'system', 'account', 'reset_password', 'parish',
     'Đặt lại mật khẩu tạm thời cho tài khoản cấp thấp hơn trong giáo xứ của mình', TRUE, 744, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, action_code = EXCLUDED.action_code,
    is_system = TRUE, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

-- Mặc định: vai trò nào đang có quyền quản lý tài khoản ở phạm vi nào thì có quyền đặt lại mật khẩu ở phạm vi đó.
INSERT INTO role_permission (id, role_code, permission_code, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by)
SELECT gen_random_uuid()::text, rp.role_code, replace(rp.permission_code, '.manage.', '.reset_password.'),
       'SYSTEM', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role_permission rp
WHERE rp.permission_code IN (
        'system.account.manage.all',
        'system.account.manage.diocese',
        'system.account.manage.deanery',
        'system.account.manage.parish')
  AND rp.status = TRUE
ON CONFLICT (role_code, permission_code) DO UPDATE
SET status = TRUE, revoked_at = NULL, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
