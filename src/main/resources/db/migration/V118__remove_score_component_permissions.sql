-- ============================================================================
-- V118: Xóa hẳn quyền và phân quyền của module thành phần điểm mẫu (đã bỏ ở V117).
--       Thành phần điểm nay khai báo trong từng khóa nên dùng quyền tạo/sửa khóa huấn luyện.
-- ============================================================================

DELETE FROM account_permission_override WHERE permission_code LIKE 'training.score_component.%';
DELETE FROM role_permission WHERE permission_code LIKE 'training.score_component.%';
DELETE FROM permission WHERE permission_code LIKE 'training.score_component.%' OR resource_code = 'score_component';
DELETE FROM permission_resource WHERE resource_code = 'score_component';
