-- ============================================================================
-- V108: Khóa giáo hạt mở đăng ký cho các giáo hạt khác cùng giáo phận.
--       - training_course.open_to_other_deaneries: giáo hạt tổ chức khóa bật khi muốn các giáo hạt khác gửi hồ sơ sang (mặc định tắt).
--       - cross_deanery_course_access: mỗi giáo hạt khác cho phép / từ chối giáo xứ của mình gửi hồ sơ sang khóa đó
--         (chưa có dòng = chưa quyết định, giáo xứ chưa được gửi).
-- ============================================================================

ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS open_to_other_deaneries BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS cross_deanery_course_access (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_code   VARCHAR(255) NOT NULL,
    deanery_id    UUID         NOT NULL,
    decision      VARCHAR(20)  NOT NULL,
    decision_note VARCHAR(500) NULL,
    decided_by    VARCHAR(100) NULL,
    decided_at    TIMESTAMP WITH TIME ZONE NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100) NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NULL,
    updated_by    VARCHAR(100) NULL,
    status        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cross_deanery_course_access UNIQUE (course_code, deanery_id),
    CONSTRAINT fk_cross_deanery_access_course FOREIGN KEY (course_code) REFERENCES training_course (course_code) ON DELETE CASCADE,
    CONSTRAINT fk_cross_deanery_access_deanery FOREIGN KEY (deanery_id) REFERENCES deanery (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_cross_deanery_access_deanery ON cross_deanery_course_access (deanery_id, decision);
