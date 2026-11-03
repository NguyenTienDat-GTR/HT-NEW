# Changelog

## 1.0.0

### Tài khoản nhiều vai trò
- Tài khoản có nhiều vai trò (ADMIN_DEANERY, ADMIN_PARISH, vai trò tùy chỉnh) dùng được hợp quyền của mọi vai trò; quyền trùng chỉ tính một lần.
  - Thêm `AccountScopeResolver` (`common/security`) và bộ lọc `BaseSearchRequest.scopeAnyOf` (nhóm điều kiện nối bằng OR, do máy chủ đặt) thay cho các nhánh `if / else if` chỉ lấy cấp đầu tiên.
  - Áp dụng cho: danh sách khóa huấn luyện, hồ sơ tham dự, chứng chỉ (danh sách và năm cấp), cấu hình chứng chỉ/thẻ tham dự, phân công ban điều hành (danh sách và năm), khóa huấn luyện quá khứ, tìm kiếm nhanh.
- Tư cách gửi đăng ký khóa huấn luyện (giáo phận, giáo hạt, giáo xứ):
  - `RegistrationSenderResolver` xác định các tư cách khả dụng theo quyền gửi và đơn vị hiệu lực của tài khoản; không còn đòi liên kết giáo hạt "trực tiếp" trên tài khoản.
  - `GET /api/training/courses/registration-senders` liệt kê các tư cách.
  - `GET /api/training/courses/registrable`, `GET .../registration-candidates` và `POST .../registrations` nhận `senderType` (tùy chọn); bỏ trống thì chọn tư cách phù hợp với khóa.
- Mỗi tài khoản tối đa 3 vai trò (tạo tài khoản, gán, gán hàng loạt, đổi vai trò, bật lại vai trò). SUPER_ADMIN và ADMIN_DIOCESE vẫn phải là vai trò duy nhất, nay kiểm tra cả ở `AccountRoleService`.

### Phiên bản hệ thống
- Phiên bản 1.0.0 (`build.gradle`, `springBoot { buildInfo() }`).
