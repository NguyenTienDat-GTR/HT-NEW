-- Khổ giấy và hướng in cho chứng nhận. Mặc định A4 ngang (khớp bố cục hiện tại).
ALTER TABLE certificate_config
    ADD COLUMN IF NOT EXISTS paper_size VARCHAR(20) NOT NULL DEFAULT 'A4',
    ADD COLUMN IF NOT EXISTS orientation VARCHAR(20) NOT NULL DEFAULT 'LANDSCAPE';
