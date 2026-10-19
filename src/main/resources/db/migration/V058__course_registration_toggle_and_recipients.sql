-- ============================================================================
-- V058: Đóng/mở đăng ký thủ công cho khóa huấn luyện và danh sách tài khoản nhận
--       danh sách đăng ký (phải có quyền duyệt danh sách đăng ký).
-- ============================================================================

ALTER TABLE training_course ADD COLUMN IF NOT EXISTS registration_open BOOLEAN NOT NULL DEFAULT TRUE;

-- Khóa đã hết hạn đăng ký thì ghi nhận là đã đóng.
UPDATE training_course SET registration_open = FALSE
WHERE registration_end_at IS NOT NULL AND registration_end_at < CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS training_course_recipient
(
    course_code VARCHAR(255) NOT NULL,
    username    VARCHAR(255) NOT NULL,
    PRIMARY KEY (course_code, username),
    CONSTRAINT fk_training_course_recipient_course
        FOREIGN KEY (course_code) REFERENCES training_course (course_code) ON DELETE CASCADE
);
