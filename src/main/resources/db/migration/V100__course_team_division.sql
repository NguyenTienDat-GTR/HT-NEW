-- Chia đội cho khóa huấn luyện (không áp dụng khóa quá khứ): tên đội của hồ sơ nằm trong danh sách tham dự.
ALTER TABLE course_participation ADD COLUMN IF NOT EXISTS team_name VARCHAR(150);
CREATE INDEX IF NOT EXISTS idx_course_participation_team_name ON course_participation (course_id, team_name);
