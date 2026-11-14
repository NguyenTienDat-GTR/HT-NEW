-- Thiết bị nhận thông báo đẩy của app di động (Expo Push Service): mỗi lần đăng nhập app đăng ký mã đẩy của máy, đăng xuất thì hủy.
-- Mỗi mã đẩy thuộc đúng một bản ghi (máy đổi tài khoản thì bản ghi cũ bị thay); mỗi tài khoản có tối đa một bản ghi cho mỗi mã thiết bị.
-- Không có khóa ngoại tới tài khoản: bản ghi cũ tự dọn khi Expo báo mã không còn hợp lệ (DeviceNotRegistered).
CREATE TABLE IF NOT EXISTS push_device (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    push_token VARCHAR(255) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    app_version VARCHAR(32),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_push_device_token ON push_device (push_token);
CREATE UNIQUE INDEX IF NOT EXISTS ux_push_device_user_device ON push_device (username, device_id);
CREATE INDEX IF NOT EXISTS ix_push_device_username ON push_device (username);
