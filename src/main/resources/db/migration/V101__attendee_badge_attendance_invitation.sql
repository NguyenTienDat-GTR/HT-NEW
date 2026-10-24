-- ============================================================================
-- V101: Thẻ tham dự khóa huấn luyện (mail merge), điểm danh bằng QR và email thông tin tham dự.
--       - training_course: cờ bật điểm danh, cờ tự gửi email thông tin tham dự khi hồ sơ được duyệt.
--       - course_participation: thời điểm điểm danh, thời điểm gửi email thông tin tham dự và lỗi gửi gần nhất.
--       - badge_config: thư viện cấu hình thẻ (khổ thẻ, mặt trước / mặt sau, ảnh nền, bố cục) theo phạm vi sở hữu.
--       - Quyền CRUD cấu hình thẻ theo phạm vi giáo phận / giáo hạt.
-- ============================================================================

ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS attendance_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_send_invitation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS checked_in_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS invitation_sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS invitation_error VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_course_participation_checked_in ON course_participation (course_id, checked_in_at);

CREATE TABLE IF NOT EXISTS badge_config
(
    id               UUID PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    scope_key        VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    paper_size       VARCHAR(20),
    paper_orientation VARCHAR(20),
    card_width_mm    NUMERIC(6, 2) NOT NULL,
    card_height_mm   NUMERIC(6, 2) NOT NULL,
    card_orientation VARCHAR(20)  NOT NULL DEFAULT 'LANDSCAPE',
    margin_mm        NUMERIC(6, 2) NOT NULL DEFAULT 10,
    gap_mm           NUMERIC(6, 2) NOT NULL DEFAULT 0,
    crop_marks       BOOLEAN      NOT NULL DEFAULT TRUE,
    has_back         BOOLEAN      NOT NULL DEFAULT FALSE,
    front_background TEXT,
    back_background  TEXT,
    front_layout     JSONB,
    back_layout      JSONB,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMP WITH TIME ZONE,
    updated_by       VARCHAR(100),
    status           BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Mỗi phạm vi tối đa một cấu hình chung, tên duy nhất (không phân biệt hoa thường) trong phạm vi.
CREATE UNIQUE INDEX IF NOT EXISTS uq_badge_config_scope_default ON badge_config (scope_key) WHERE is_default;
CREATE UNIQUE INDEX IF NOT EXISTS uq_badge_config_scope_name ON badge_config (scope_key, lower(name));

INSERT INTO permission_resource (
    resource_code, module_code, resource_name, description, display_order,
    status, created_at, created_by, updated_at, updated_by
)
VALUES (
    'badge_config', 'training', 'Cấu hình thẻ tham dự',
    'Thư viện cấu hình thẻ tham dự khóa huấn luyện: khổ thẻ, mặt trước / sau, ảnh nền và mail merge', 46,
    TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
)
ON CONFLICT (resource_code) DO UPDATE
SET module_code = EXCLUDED.module_code,
    resource_name = EXCLUDED.resource_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.badge_config.read.diocese', 'Xem cấu hình thẻ tham dự - Giáo phận', 'training', 'badge_config', 'read', 'diocese',
     'Xem thư viện cấu hình thẻ tham dự của giáo phận', TRUE, 520, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.create.diocese', 'Tạo cấu hình thẻ tham dự - Giáo phận', 'training', 'badge_config', 'create', 'diocese',
     'Tạo cấu hình thẻ tham dự trong thư viện của giáo phận', TRUE, 522, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.update.diocese', 'Cập nhật cấu hình thẻ tham dự - Giáo phận', 'training', 'badge_config', 'update', 'diocese',
     'Sửa và đặt cấu hình thẻ tham dự chung trong giáo phận', TRUE, 521, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.delete.diocese', 'Xóa cấu hình thẻ tham dự - Giáo phận', 'training', 'badge_config', 'delete', 'diocese',
     'Xóa cấu hình thẻ tham dự trong thư viện của giáo phận', TRUE, 523, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.read.deanery', 'Xem cấu hình thẻ tham dự - Giáo hạt', 'training', 'badge_config', 'read', 'deanery',
     'Xem thư viện cấu hình thẻ tham dự của giáo hạt', TRUE, 530, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.create.deanery', 'Tạo cấu hình thẻ tham dự - Giáo hạt', 'training', 'badge_config', 'create', 'deanery',
     'Tạo cấu hình thẻ tham dự trong thư viện của giáo hạt', TRUE, 532, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.update.deanery', 'Cập nhật cấu hình thẻ tham dự - Giáo hạt', 'training', 'badge_config', 'update', 'deanery',
     'Sửa và đặt cấu hình thẻ tham dự chung trong giáo hạt', TRUE, 531, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.badge_config.delete.deanery', 'Xóa cấu hình thẻ tham dự - Giáo hạt', 'training', 'badge_config', 'delete', 'deanery',
     'Xóa cấu hình thẻ tham dự trong thư viện của giáo hạt', TRUE, 533, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    action_code = EXCLUDED.action_code,
    is_system = TRUE,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

-- Gán cho vai trò quản trị: super admin và admin giáo phận (quyền giáo phận), admin giáo hạt (quyền giáo hạt).
INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, r.role_code, p.permission_code, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'SYSTEM', CURRENT_TIMESTAMP
FROM role r
JOIN permission p ON p.resource_code = 'badge_config'
WHERE (
        r.role_code = 'SUPER_ADMIN' AND p.scope_code = 'diocese'
        OR r.role_code = 'ADMIN_DIOCESE' AND p.scope_code = 'diocese'
        OR r.role_code = 'ADMIN_DEANERY' AND p.scope_code = 'deanery'
    )
ON CONFLICT (role_code, permission_code) DO UPDATE
SET expires_at = NULL, revoked_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
