# Sao lưu tự động DB production lên Google Drive

- Workflow: `.github/workflows/db-backup.yml`; script: `deploy/db-backup.sh`.
- Lịch: chạy mỗi đêm lúc 01:00 giờ VN (cron `0 18 * * *` UTC). Script chỉ sao lưu khi bản gần nhất trên Drive đã cách từ 3 ngày trở lên (so theo ngày trong tên file), nên thực tế là 3 ngày/lần: 19/11, 22/11, 25/11, 28/11, … Lỡ một lần thì đêm sau tự bù.
- File: `ht_management_YYYY-MM-DD.dump` (định dạng custom của `pg_dump`, khôi phục bằng `pg_restore`). Thư mục Drive đích: ID `1Dkjy4k4mOMjyEqNxPiDlhinAQhtQGtdf`.
- Cuốn chiếu: tải bản mới lên, xác nhận có trên Drive rồi mới xóa các bản cũ (xóa vĩnh viễn, không vào thùng rác), chỉ giữ 3 file mới nhất. Dump lỗi hoặc quá nhỏ thì dừng, không xóa gì.
- Kết nối DB: `cloudflared access tcp` tới `db.clead.io.vn` bằng service token Cloudflare Access, rồi `pg_dump` 18 (client 17 bị server từ chối).

## Secret cần có (GitHub, secret cấp repo của BE)

| Secret | Nội dung |
|---|---|
| `PROD_DB_PASSWORD` | Mật khẩu user `qlht` của DB `ht_management` |
| `CF_ACCESS_CLIENT_ID`, `CF_ACCESS_CLIENT_SECRET` | Đã có. Service token này phải được chính sách Cloudflare Access của `db.clead.io.vn` cho phép |
| `GDRIVE_RCLONE_TOKEN` | Token OAuth của rclone (JSON một dòng), tạo như bên dưới |

Tạo `GDRIVE_RCLONE_TOKEN` (làm một lần, trên máy có trình duyệt, đăng nhập đúng tài khoản Google sở hữu thư mục):

```bash
rclone authorize "drive" "{\"scope\":\"drive\"}"
```

Dán toàn bộ chuỗi JSON in ra (từ `{"access_token":...` đến `}`) vào secret. Quyền `drive` (toàn bộ) là cần thiết vì thư mục đã tồn tại; script chỉ đụng file đúng mẫu `ht_management_YYYY-MM-DD.dump` trong thư mục đó. Nếu app OAuth ở trạng thái "Testing", refresh token hết hạn sau 7 ngày: chuyển app sang "In production" hoặc dùng client_id/secret riêng đã publish.

## Chạy tay

Actions > DB Backup > Run workflow. Tùy chọn `force` (bỏ qua khoảng cách 3 ngày) và `dry_run` (chỉ dump + kiểm tra, không động tới Drive). Nên chạy `dry_run` trước để kiểm tra tunnel và mật khẩu, rồi chạy `force` để có bản đầu tiên.

## Lưu ý

- File dump chứa toàn bộ dữ liệu cá nhân của huynh trưởng: chỉ chia sẻ thư mục Drive cho người cần thiết.
- GitHub chỉ chạy lịch trên nhánh mặc định (`main`), có thể trễ vài phút; workflow lịch bị tắt nếu repo không hoạt động 60 ngày.
- Khôi phục: tải file về, `pg_restore --no-owner -d <db_đích> ht_management_YYYY-MM-DD.dump` (client 18).
