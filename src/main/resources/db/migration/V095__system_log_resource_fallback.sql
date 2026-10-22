-- Dòng nhật ký cũ có đường dẫn không nằm trong danh sách đã biết: lấy đoạn tài nguyên đầu tiên của đường dẫn làm đối tượng
-- (bỏ phân hệ system/training) để cột "Đối tượng" không còn trống.
UPDATE system_log
SET resource = lower(replace(
        CASE
            WHEN split_part(request_path, '/', 3) IN ('system', 'training') THEN split_part(request_path, '/', 4)
            ELSE split_part(request_path, '/', 3)
            END, '-', '_'))
WHERE resource IS NULL
  AND request_path LIKE '/api/%'
  AND CASE
          WHEN split_part(request_path, '/', 3) IN ('system', 'training') THEN split_part(request_path, '/', 4)
          ELSE split_part(request_path, '/', 3)
          END <> '';
