-- Đặt lại mật khẩu role PostgreSQL theo POSTGRES_PASSWORD của container (xem bước deploy trong .github/workflows/cd.yml).
-- Biến usr và pw do psql truyền vào từ biến môi trường của container, không có giá trị nào nằm trong file này.
ALTER ROLE :"usr" WITH PASSWORD :'pw';
