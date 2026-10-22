-- Bổ sung thông tin truy vết cho nhật ký hệ thống: thao tác nghiệp vụ, tên đối tượng, bộ lọc yêu cầu và thời gian xử lý.
ALTER TABLE system_log
    ADD COLUMN IF NOT EXISTS operation     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS resource_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS query_string  VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS duration_ms   INTEGER;

CREATE INDEX IF NOT EXISTS idx_system_log_resource ON system_log (resource, resource_id);
CREATE INDEX IF NOT EXISTS idx_system_log_operation ON system_log (operation);

-- Dòng cũ chưa gắn đối tượng: suy ra từ đường dẫn để cột "Đối tượng" không còn trống.
UPDATE system_log
SET resource = CASE
                   WHEN request_path LIKE '/api/system/accounts%' THEN 'account'
                   WHEN request_path LIKE '/api/system/roles%' THEN 'role'
                   WHEN request_path LIKE '/api/system/permissions%' THEN 'permission'
                   WHEN request_path LIKE '/api/system/notifications%' THEN 'notification'
                   WHEN request_path LIKE '/api/leader-levels%' THEN 'leader_level'
                   WHEN request_path LIKE '/api/training/course-types%' THEN 'course_type'
                   WHEN request_path LIKE '/api/training/score-components%' THEN 'score_component'
                   WHEN request_path LIKE '/api/training/participations%' OR request_path LIKE '/api/training/registrations%' THEN 'participation'
                   WHEN request_path LIKE '/api/analytics%' THEN 'analytics'
                   WHEN request_path LIKE '/api/auth%' THEN 'auth'
                   ELSE resource
    END
WHERE resource IS NULL;

-- Đoạn cuối đường dẫn như "me", "profile", "filter-options" không phải mã đối tượng: bỏ để không gây hiểu nhầm khi truy vết.
UPDATE system_log
SET resource_id = NULL
WHERE resource_id IS NOT NULL
  AND resource_id !~ '[0-9]'
  AND resource NOT IN ('account', 'role', 'permission');

UPDATE system_log
SET operation = CASE
                    WHEN http_method = 'GET' AND resource_id IS NULL THEN 'LIST'
                    WHEN http_method = 'GET' THEN 'VIEW'
                    WHEN http_method = 'POST' THEN 'CREATE'
                    WHEN http_method IN ('PUT', 'PATCH') THEN 'UPDATE'
                    WHEN http_method = 'DELETE' THEN 'DELETE'
                    ELSE http_method
    END
WHERE operation IS NULL;
