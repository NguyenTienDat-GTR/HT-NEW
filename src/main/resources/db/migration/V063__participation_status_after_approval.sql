-- ============================================================================
-- V063: Trạng thái tham dự theo kết quả duyệt đăng ký:
--       duyệt xong -> ATTENDED, từ chối duyệt -> REJECTED.
-- ============================================================================

UPDATE course_participation
SET participation_status = 'ATTENDED'
WHERE approval_status = 'APPROVED' AND participation_status = 'REGISTERED';

UPDATE course_participation
SET participation_status = 'REJECTED'
WHERE approval_status = 'REJECTED' AND participation_status = 'REGISTERED';
