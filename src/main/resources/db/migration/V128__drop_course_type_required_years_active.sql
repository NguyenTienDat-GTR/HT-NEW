-- Số năm hoạt động đã chuyển hết sang từng khóa ở V127 và code không còn đọc/ghi cột này.
ALTER TABLE training_course_type DROP COLUMN IF EXISTS required_years_active;
