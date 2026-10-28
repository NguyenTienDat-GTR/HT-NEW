-- Nhiều giáo xứ / giáo hạt chưa thành lập xứ đoàn / hiệp đoàn nên tên xứ đoàn và tên hiệp đoàn không còn bắt buộc.
ALTER TABLE parish ALTER COLUMN chapter_name DROP NOT NULL;
ALTER TABLE deanery ALTER COLUMN association_name DROP NOT NULL;
