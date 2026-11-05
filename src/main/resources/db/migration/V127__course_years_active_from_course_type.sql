-- Số năm hoạt động chỉ còn đặt ở từng khóa huấn luyện (training_course.years_active), loại khóa không còn cấu hình số năm.
-- Giữ nguyên yêu cầu cũ: khóa lấy giá trị lớn hơn giữa số năm của khóa và của loại khóa (đúng cách kiểm tra điều kiện trước đây).
-- Cột training_course_type.required_years_active được giữ lại (không còn được code đọc/ghi) để bản chạy code cũ không lỗi trong lúc triển khai;
-- sẽ xóa cột ở một migration sau khi mọi môi trường đã chạy code mới.
UPDATE training_course c
SET years_active = t.required_years_active
FROM training_course_type t
WHERE c.course_type_code = t.type_code
  AND t.required_years_active IS NOT NULL
  AND t.required_years_active > c.years_active;
