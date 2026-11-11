-- Import Excel hồ sơ huynh trưởng / danh sách tham dự: dòng nghi trùng với hồ sơ đã có được giữ lại chờ người dùng xác nhận (các dòng còn lại
-- đã import xong). Giữ bản sao file Excel (và ZIP ảnh) của lần import đó để xử lý tiếp các dòng nghi trùng sau khi người dùng xác nhận.
ALTER TABLE excel_job
    ADD COLUMN IF NOT EXISTS pending_input_path VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pending_images_path VARCHAR(500);
