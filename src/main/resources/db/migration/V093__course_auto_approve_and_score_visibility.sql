-- Khóa chỉ được tự duyệt hồ sơ hợp lệ khi bật cờ; hiển thị điểm cho cấp thấp hơn cũng phải bật cờ.
-- Khóa đã có giữ nguyên hành vi hiện tại (đang tự duyệt và đang hiển thị điểm); khóa tạo mới mặc định tắt.
ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS auto_approve_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS score_visible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE training_course
    ALTER COLUMN auto_approve_eligible SET DEFAULT FALSE,
    ALTER COLUMN score_visible SET DEFAULT FALSE;
