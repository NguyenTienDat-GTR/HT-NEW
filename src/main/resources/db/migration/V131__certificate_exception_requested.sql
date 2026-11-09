-- Chứng nhận ngoại lệ do người chấm điểm tạo cho hồ sơ có kết quả không đạt (chờ duyệt ở tab duyệt chứng nhận, bắt buộc nhập lý do khi duyệt).
ALTER TABLE certificate ADD COLUMN IF NOT EXISTS exception_requested BOOLEAN NOT NULL DEFAULT FALSE;
