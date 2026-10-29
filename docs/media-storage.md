# Lưu trữ ảnh trên cloud (media)

Ảnh đại diện huynh trưởng, ảnh nền chứng nhận và ảnh nền thẻ tham dự được lưu trên cloud (mặc định Cloudinary), không còn lưu base64 trong database.

## Cách hoạt động

```
FE nén ảnh ──multipart──▶ POST /api/media ──▶ BE kiểm tra + tạo biến thể ──▶ MediaStorageProvider (Cloudinary | local | ...)
FE <img src="/backend/api/media/{assetId}/{variant}?sig"> ──▶ BE kiểm tra đăng nhập + chữ ký ──▶ trả byte ảnh
```

- Database chỉ giữ `media_asset` (siêu dữ liệu, khóa lưu trữ logic, sha256) và id asset trong bản ghi nghiệp vụ
  (`leader.image_asset_id`, `certificate_config.background_asset_id`, `badge_config.front/back_background_asset_id`).
  **Không có URL của nhà cung cấp nào được lưu hoặc gửi cho frontend.**
- Ảnh lưu ở chế độ riêng tư (Cloudinary `type=authenticated`), chỉ backend đọc được.

### Phân quyền xem ảnh

1. Backend chỉ ký URL khi trả bản ghi cho người đã qua kiểm tra quyền đọc bản ghi đó (các `*AccessEvaluator` hiện có).
2. Mở URL ảnh phải **đăng nhập** (cookie `accessToken`) **và** chữ ký HMAC hợp lệ. Link ảnh **không có thời hạn** (ảnh luôn hiển thị trong danh sách), nhưng người ngoài có link mà chưa đăng nhập vẫn nhận 401, chữ ký sai nhận 403.
3. Mỗi ảnh có một URL cố định nên trình duyệt cache được (`Cache-Control: private` + `ETag`/304).

### Thư mục hiển thị trên Cloudinary (dynamic folder mode)

Tài khoản Cloudinary mới dùng "dynamic folder mode": thư mục trong Media Library là `asset_folder`, **không** suy ra từ `public_id`. Vì vậy `CloudinaryStorageProvider` truyền `asset_folder` = thư mục của khóa logic khi tải lên và đặt lại sau khi đổi vị trí (`move`), nên ảnh nằm đúng `…/dioceses/{giáo-phận}/avatars|account-avatars|certificate-backgrounds|badge-backgrounds/{assetId}/`. Tài khoản fixed folder mode không nhận `asset_folder` thì tự bỏ tham số (thư mục đã nằm trong `public_id`).
Ảnh tải lên trước khi có thay đổi này đang nằm ở thư mục gốc "Home": đặt `CLOUDINARY_SYNC_FOLDERS=true` một lần rồi khởi động lại để đưa chúng vào đúng thư mục (`app.media.cloudinary.sync-folders-on-startup`), xong đặt lại `false`.

### Thư mục theo giáo phận và loại ảnh

```
{root-prefix}/dioceses/{slug-tên-giáo-phận}-{8 ký tự id}/avatars/{assetId}/{thumb|medium}.jpg
{root-prefix}/dioceses/{...}/certificate-backgrounds/{assetId}/{master|preview}.jpg
{root-prefix}/dioceses/{...}/badge-backgrounds/{assetId}/{master|preview}.jpg
{root-prefix}/system/...        (cấu hình dùng chung toàn hệ thống)
{root-prefix}/pending/...       (mới tải lên, chưa lưu vào bản ghi; tự dọn sau 24 giờ)
```

Tên thư mục giáo phận lưu ở `diocese.storage_folder`, sinh khi tạo giáo phận và **không đổi** khi đổi tên giáo phận.
Huynh trưởng chuyển sang giáo phận khác thì job dọn dẹp tự chuyển avatar sang thư mục giáo phận mới.

### Tối ưu dung lượng

- FE: cắt vuông + thu nhỏ avatar (tối đa 512px), thu nhỏ ảnh nền (tối đa 3508px), JPEG chất lượng 0.85–0.9.
- BE: kiểm tra magic bytes + giải mã thật, chặn ảnh quá nhiều điểm ảnh, xoay theo EXIF, bỏ metadata, tạo biến thể:
  avatar `thumb` 96px (~vài KB, dùng cho danh sách/header) và `medium` 320px; nền `master` ≤3508px (dùng dựng PDF) và `preview` 1600px.
- Danh sách 100 huynh trưởng chỉ tải 100 ảnh thumb nhỏ, tải lười (`loading="lazy"`), có bộ nhớ đệm trong BE cho biến thể nhỏ.

## Cấu hình (biến môi trường)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `MEDIA_ACTIVE_PROVIDER` | trống = tự chọn: có `CLOUDINARY_URL` thì Cloudinary, không thì `local` | Nhà cung cấp ghi ảnh mới |
| `CLOUDINARY_URL` | rỗng | `cloudinary://API_KEY:API_SECRET@CLOUD_NAME` (bắt buộc khi dùng Cloudinary) |
| `MEDIA_ROOT_PREFIX` | `ht/dev` / `ht/prod` | Tiền tố gốc, tách môi trường trên cùng tài khoản cloud |
| `MEDIA_SIGNING_SECRET` | rỗng | Khóa ký URL ảnh. Nếu trống sẽ suy ra từ `JWT_ACCESS_SECRET` — **nên đặt riêng ở production** |
| `MEDIA_LOCAL_ENABLED` / `MEDIA_LOCAL_DIR` | `true` (dev) / `false` (prod), `./uploads/media` | Nhà cung cấp thư mục cục bộ |
| `MEDIA_LEGACY_MIGRATION_ENABLED` | `true` | Job chuyển ảnh base64 cũ lên cloud |
| `MEDIA_PROVIDER_MIGRATION_ENABLED` | `true` | Job chuyển ảnh sang nhà cung cấp mới |
| `MEDIA_PROVIDER_MIGRATION_DELETE_SOURCE` | `false` | Xóa bản trên nhà cung cấp cũ sau khi đã chép và đối chiếu sha256 |

## Triển khai lần đầu (chuyển ảnh base64 hiện có)

1. Đặt `CLOUDINARY_URL` (và `MEDIA_SIGNING_SECRET`) rồi khởi động BE; migration `V110` tự chạy và điền `diocese.storage_folder`.
2. Job `LegacyMigration` tự chuyển từng lô ảnh base64 trong `leader`, `certificate_config`, `badge_config` lên cloud, rồi xóa nội dung base64 của dòng đó.
   Trong lúc chưa chuyển xong, bản ghi chưa có asset vẫn trả ảnh base64 cũ nên hệ thống không gián đoạn.
3. Theo dõi tiến độ: `GET /api/media/admin/status` (super admin) và truy vấn
   `select count(*) from leader where image_url like 'data:%'` (tương tự hai bảng còn lại) về 0.
4. Khi cả ba về 0 và đã chạy ổn định, bản phát hành sau có thể drop các cột base64 cũ
   (`leader.image_url`, `certificate_config.background_image`, `badge_config.front_background/back_background`).

## Đổi sang nhà cung cấp khác (S3, R2, MinIO, ...)

1. Viết một lớp `@Component` implement `MediaStorageProvider` (`name`, `put`, `get`, `delete`, tùy chọn `move`/`ensureFolder`).
   Khóa lưu trữ là chuỗi logic nên dùng lại nguyên vẹn, không phụ thuộc định dạng URL của nhà cung cấp.
2. Cấu hình provider mới **và giữ nguyên cấu hình provider cũ** (ví dụ vẫn đặt `CLOUDINARY_URL`), rồi đổi `MEDIA_ACTIVE_PROVIDER=<tên mới>`.
3. Khởi động lại. Ảnh mới ghi vào provider mới; job `ProviderMigration` tự chép từng biến thể từ provider cũ sang mới,
   đối chiếu sha256 hai đầu rồi mới đổi `media_asset.provider`. Trong lúc chuyển, mỗi ảnh vẫn được đọc từ provider ghi trong `media_asset`.
4. `GET /api/media/admin/status` hiển thị số ảnh theo provider; khi toàn bộ ảnh `ACTIVE` đã ở provider mới có thể gỡ cấu hình cũ.
   Muốn dọn bản cũ trên provider cũ, bật `MEDIA_PROVIDER_MIGRATION_DELETE_SOURCE=true` trước khi chuyển.
5. Lỗi từng ảnh được ghi vào `media_asset.migration_error` và tự thử lại với backoff (tối đa 8 lần).

## Dọn dẹp tự động

Mỗi giờ: ảnh `PENDING` quá 24 giờ và ảnh `ACTIVE` không còn bản ghi tham chiếu bị đánh dấu `DELETED`;
ảnh `DELETED` quá 7 ngày bị xóa tệp trên cloud rồi xóa dòng.
