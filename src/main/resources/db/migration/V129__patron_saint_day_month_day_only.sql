-- Ngày bổn mạng chỉ lưu ngày và tháng (không có năm), dạng "MM-dd".
ALTER TABLE diocese
    ALTER COLUMN patron_saint_day TYPE VARCHAR(5) USING to_char(patron_saint_day, 'MM-DD');
ALTER TABLE deanery
    ALTER COLUMN patron_saint_day TYPE VARCHAR(5) USING to_char(patron_saint_day, 'MM-DD');
ALTER TABLE parish
    ALTER COLUMN patron_saint_day TYPE VARCHAR(5) USING to_char(patron_saint_day, 'MM-DD');
