-- Ẩn khỏi mọi bộ lọc phân loại quyền hai đối tượng không còn quyền nào (các quyền của chúng đã bị vô hiệu hóa ở V049, V056, V057):
-- "Điều kiện khóa huấn luyện" (requirement) và "Công thức điểm" (score_formula). Chỉ tắt đối tượng trong bảng phân loại, không xóa dòng nào.
UPDATE permission_resource
SET status = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM'
WHERE resource_code IN ('requirement', 'score_formula')
  AND status = TRUE;
