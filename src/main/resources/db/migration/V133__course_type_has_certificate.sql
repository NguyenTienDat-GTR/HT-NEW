-- Tách "có cấp chứng nhận" khỏi "cấp độ khi hoàn thành": cờ này chỉ lo việc cấp chứng nhận.
-- Thăng cấp: loại khóa có cấp chứng nhận thì theo chứng nhận được duyệt, ngược lại theo kết quả khóa.
ALTER TABLE training_course_type ADD COLUMN IF NOT EXISTS has_certificate BOOLEAN NOT NULL DEFAULT FALSE;

-- Giữ nguyên hành vi cũ: loại khóa đã có cấp độ khi hoàn thành là loại khóa có chứng nhận.
UPDATE training_course_type
SET has_certificate = TRUE
WHERE resulting_level IS NOT NULL AND resulting_level <> 'NONE';
