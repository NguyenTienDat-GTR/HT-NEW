-- Dữ liệu chi tiết (JSON) của thông báo: danh sách hồ sơ được gom nhóm, danh sách quyền vừa phân...
ALTER TABLE training_notification
    ADD COLUMN IF NOT EXISTS payload TEXT;

-- Thông báo cũ tự duyệt đăng ký lưu tiếng Việt không dấu và mã khóa: chuyển sang có dấu và tên khóa.
UPDATE training_notification n
SET title = 'Tự động duyệt danh sách đăng ký',
    message = regexp_replace(
            n.message,
            ' da duoc he thong xu ly ([0-9]+) dang ky cho khoa .*$',
            ' đã được hệ thống xử lý \1 đăng ký cho khóa ' || COALESCE(c.course_name, n.course_code)
    )
FROM training_course c
WHERE c.course_code = n.course_code
  AND n.event_type = 'training.registration.auto_approved';

UPDATE training_notification
SET title = 'Tự động duyệt danh sách đăng ký'
WHERE event_type = 'training.registration.auto_approved'
  AND title = 'Tu dong duyet danh sach dang ky';

-- Thông báo gửi danh sách đăng ký: thay mã khóa bằng tên khóa.
UPDATE training_notification n
SET message = regexp_replace(
        n.message,
        ' đăng ký cho khóa .*$',
        ' đăng ký cho khóa ' || c.course_name
)
FROM training_course c
WHERE c.course_code = n.course_code
  AND n.event_type = 'training.registration.submitted'
  AND n.message LIKE '% đăng ký cho khóa %';

-- Thông báo đổi trạng thái vai trò/quyền: bỏ mã trong ngoặc, chỉ giữ tên.
UPDATE training_notification
SET message = regexp_replace(message, ' \([A-Za-z0-9_.:-]+\)', '', 'g')
WHERE event_type IN ('ROLE_TOGGLED', 'PERMISSION_TOGGLED', 'ACCOUNT_ROLE_TOGGLED',
                     'ROLE_PERMISSION_TOGGLED', 'ACCOUNT_PERMISSION_TOGGLED');
