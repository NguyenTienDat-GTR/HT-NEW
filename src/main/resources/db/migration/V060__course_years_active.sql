-- ============================================================================
-- V060: Số năm hoạt động của khóa huấn luyện (điều kiện kèm theo khóa tiên quyết).
--       Năm tổ chức khóa - năm đạt khóa tiên quyết phải >= years_active. Mặc định 0, không âm.
-- ============================================================================

ALTER TABLE training_course ADD COLUMN IF NOT EXISTS years_active INTEGER NOT NULL DEFAULT 0;
ALTER TABLE training_course ADD CONSTRAINT chk_training_course_years_active CHECK (years_active >= 0);
