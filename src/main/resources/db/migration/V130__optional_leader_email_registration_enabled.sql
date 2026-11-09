-- Email huynh trưởng không còn bắt buộc (unique vẫn giữ: NULL không bị coi là trùng).
ALTER TABLE leader ALTER COLUMN email DROP NOT NULL;

-- Khóa có mở đăng ký hay không. Khóa đã có giữ nguyên hành vi hiện tại (mở đăng ký); khóa quá khứ không đăng ký nên để FALSE.
ALTER TABLE training_course ADD COLUMN IF NOT EXISTS registration_enabled BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE training_course SET registration_enabled = FALSE WHERE is_past_course;
