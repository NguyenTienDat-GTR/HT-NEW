-- Mẫu Excel danh sách đăng ký tham dự (file do người dùng tự soạn, trường chèn dạng {{tên trường}}) dùng khi xuất danh sách đăng ký của một khóa.
-- Mỗi đơn vị (giáo phận hoặc giáo hạt) một mẫu; khóa cấp giáo hạt dùng mẫu của giáo hạt, chưa có thì dùng mẫu của giáo phận.
CREATE TABLE IF NOT EXISTS registration_export_template (
    id UUID PRIMARY KEY,
    unit_type VARCHAR(20) NOT NULL CHECK (unit_type IN ('DIOCESE', 'DEANERY')),
    unit_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    -- File .xlsx lưu ở kho lưu trữ (Cloudinary), mỗi đơn vị một khóa cố định nên lưu bản mới là ghi đè bản cũ.
    storage_provider VARCHAR(30) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    -- file_size: dung lượng file gốc; compressed_size: dung lượng bản nén đang nằm trên kho lưu trữ (kho chỉ giữ bản nén).
    file_size BIGINT,
    compressed_size BIGINT,
    mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_registration_export_template_unit ON registration_export_template (unit_type, unit_id);

INSERT INTO permission_resource (
    resource_code, module_code, resource_name, description, display_order,
    status, created_at, created_by, updated_at, updated_by
)
VALUES (
    'registration_template', 'training', 'Registration list template',
    'File Excel dùng khi xuất danh sách đăng ký tham dự khóa huấn luyện', 46,
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
    ('training.registration_template.update.diocese', 'Chỉnh sửa file xuất danh sách đăng ký - Giáo phận',
     'training', 'registration_template', 'update', 'diocese',
     'Soạn, lưu và xóa file Excel mẫu dùng khi xuất danh sách đăng ký tham dự của giáo phận', TRUE, 510, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.registration_template.update.deanery', 'Chỉnh sửa file xuất danh sách đăng ký - Giáo hạt',
     'training', 'registration_template', 'update', 'deanery',
     'Soạn, lưu và xóa file Excel mẫu dùng khi xuất danh sách đăng ký tham dự của giáo hạt', TRUE, 511, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = TRUE,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

-- Quyền chỉ được tạo, không gán cho vai trò nào: quản trị tự phân quyền.
