# Bảo mật với Cloudflare (gói Free)

Phần trong repo (Caddyfile, compose, Turnstile) đã làm sẵn. Phần dưới đây là các bước **làm trên dashboard Cloudflare / AWS**,
vì cần đăng nhập tài khoản của bạn.

## 1. DNS + proxy + SSL
1. Thêm tên miền vào Cloudflare, đổi nameserver tại nơi mua tên miền.
2. Tạo bản ghi `A` cho `@`, `*` (wildcard, cho subdomain giáo phận) và host test, trỏ về IP EC2, bật **Proxied** (đám mây cam).
3. SSL/TLS > Overview: chọn **Full (strict)**. Caddy vẫn tự xin chứng chỉ Let's Encrypt nên origin có chứng chỉ hợp lệ.
4. SSL/TLS > Edge Certificates: bật **Always Use HTTPS**, **Automatic HTTPS Rewrites**, **Minimum TLS 1.2**, bật **HSTS**.
   Universal SSL chỉ phủ `@` và `*.tên-miền` (một cấp), đủ cho `<subdomain-giáo-phận>.<tên-miền>`.
5. Trang giáo phận dùng **tên miền riêng khác** (không nằm trên Cloudflare của bạn) vẫn chạy trực tiếp qua Caddy như cũ.
6. Cloudflare Free giới hạn upload 100 MB mỗi request qua proxy.

## 2. Chỉ cho Cloudflare vào cổng 80/443
AWS EC2 > Security Group: inbound 80 và 443 chỉ cho các dải IP trong https://www.cloudflare.com/ips (không để 0.0.0.0/0).
Lưu ý: tên miền riêng của giáo phận không qua Cloudflare sẽ không vào được nếu làm bước này; nếu dùng loại tên miền đó thì bỏ qua bước 2.
Danh sách dải IP trong `deploy/Caddyfile` (khối `servers`) cũng cần cập nhật khi Cloudflare đổi.

Kiểm tra IP thật: đăng nhập sai vài lần rồi xem log BE/audit; IP phải là IP của bạn, không phải 104.x/172.64.x.

## 3. Tunnel + Access (đóng cổng 22, bảo vệ pgAdmin/DB)
1. Zero Trust > Networks > Tunnels > Create tunnel (cloudflared), copy token vào `.env`: `CLOUDFLARE_TUNNEL_TOKEN=...`.
2. Trên server: `docker compose -f compose.prod.yaml --profile tunnel up -d cloudflared`.
3. Public hostname: `ssh.<tên-miền>` -> `ssh://host.docker.internal:22` (hoặc IP private của server); `db.<tên-miền>` -> `tcp://postgres:5432`.
4. Zero Trust > Access > Applications: tạo ứng dụng cho `ssh.` và `db.`, policy chỉ cho email của bạn/đội (One-time PIN hoặc Google).
5. Máy cá nhân: `cloudflared access tcp --hostname db.<tên-miền> --url 127.0.0.1:15432`, rồi pgAdmin nối `127.0.0.1:15432`.
6. Khi chạy ổn mới đóng cổng 22 trong Security Group. Giữ một cách vào dự phòng (EC2 Instance Connect/Session Manager).

## 4. Rate limiting + Turnstile
- BE đã rate limit đăng nhập theo IP bằng Redis. Thêm một lớp ở biên: Security > WAF > Rate limiting rules:
  `URI Path` equals `/backend/api/auth/login` (và `/backend/api/auth/forgot-password/*`), 10 request / 10 giây theo IP, Action Block.
  Gói Free chỉ có 1 rule nên chọn endpoint đăng nhập.
- Turnstile: Cloudflare > Turnstile > Add widget, thêm các hostname (tên miền chính và `*.<tên-miền>`), loại Managed.
  Điền `TURNSTILE_SITE_KEY` và `TURNSTILE_SECRET_KEY` vào `.env` rồi deploy lại BE. Bỏ trống = tắt.
  Khóa thử nghiệm của Cloudflare: site `1x00000000000000000000AA` / secret `1x0000000000000000000000000000000AA` (luôn pass).

## 5. WAF + Bot
- Security > WAF > Managed rules: bật **Cloudflare Managed Ruleset** (Free).
- Security > Bots: bật **Bot Fight Mode**. Nếu thấy chặn nhầm webhook/app, thêm Custom Rule cho phép.
- Custom rules (tối đa 5) gợi ý: chặn `/actuator`, `/swagger-ui`, `/v3/api-docs` từ bên ngoài; thử thách (Managed Challenge) ngoài Việt Nam cho `/backend/api/auth/*`.
- Security > Settings: Browser Integrity Check bật.

## 6. IP thật ở BE
Đã xử lý trong `deploy/Caddyfile`: Caddy chỉ tin `CF-Connecting-IP` khi kết nối đến từ dải IP Cloudflare, rồi đặt
`X-Forwarded-For` bằng IP đó; BE (Tomcat `forward-headers-strategy=native`) đọc ra IP client thật cho rate limit và nhật ký.
