-- Số năm tối đa kể từ lần đạt khóa tiên quyết gần nhất mà huynh trưởng còn được đăng ký khóa thuộc loại này (null = không giới hạn).
ALTER TABLE training_course_type ADD COLUMN IF NOT EXISTS max_years_after_prerequisite INTEGER;
