-- ============================================================================
-- V144: Giờ bắt đầu / kết thúc của khóa huấn luyện.
--       - training_course: start_time, end_time (giờ Việt Nam) đi cùng start_date, end_date.
--       - Khóa đã có: bắt đầu 00:00, kết thúc 23:59 (giữ nguyên hành vi tính theo ngày trước đây).
-- ============================================================================

ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS start_time TIME NOT NULL DEFAULT TIME '00:00',
    ADD COLUMN IF NOT EXISTS end_time TIME NOT NULL DEFAULT TIME '23:59';
