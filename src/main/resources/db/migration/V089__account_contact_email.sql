-- Email liên hệ của tài khoản không gắn Huynh Trưởng (SUPER_ADMIN, ADMIN_DIOCESE).
-- Dùng để gửi OTP đổi mật khẩu. Tài khoản gắn Huynh Trưởng dùng email của Huynh Trưởng.
ALTER TABLE account ADD COLUMN IF NOT EXISTS email VARCHAR(255);
