-- Mỗi giáo phận có thể có một subdomain riêng (vd: phucuong -> phucuong.<tên-miền-chính>).
-- Tài khoản thuộc giáo phận chỉ đăng nhập được ở subdomain của giáo phận mình.
ALTER TABLE diocese
    ADD COLUMN IF NOT EXISTS site_subdomain VARCHAR(63);

CREATE UNIQUE INDEX IF NOT EXISTS uk_diocese_site_subdomain
    ON diocese (lower(site_subdomain))
    WHERE site_subdomain IS NOT NULL;
