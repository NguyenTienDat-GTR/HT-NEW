-- Người ký ghi trực tiếp vào từng chứng nhận (xác định khi duyệt), không cấu hình chung nữa.
ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS signer_type  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS signer_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS signer_title VARCHAR(255);

-- Bỏ cấu hình người ký khỏi cấu hình chứng nhận.
ALTER TABLE certificate_config
    DROP COLUMN IF EXISTS signer_type,
    DROP COLUMN IF EXISTS signer_name,
    DROP COLUMN IF EXISTS signer_title;
