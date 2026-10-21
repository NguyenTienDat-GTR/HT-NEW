-- Ghi chú của hồ sơ trong tab chấm điểm của khóa huấn luyện (khác với nhận xét/lý do duyệt).
ALTER TABLE course_participation ADD COLUMN IF NOT EXISTS score_note TEXT;
