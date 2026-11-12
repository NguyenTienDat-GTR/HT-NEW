-- Quyền chỉ sửa ngày sinh huynh trưởng, tách khỏi quyền chỉnh sửa hồ sơ; cùng các phạm vi với quyền chỉnh sửa (giáo phận, giáo hạt, giáo xứ).
-- Chỉ tạo quyền, không gán cho vai trò nào: quản trị tự phân quyền.
INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('update_birth_date', 'Sửa ngày sinh', 'Chỉ sửa ngày sinh của đối tượng, không sửa các thông tin khác', 99, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET action_name = EXCLUDED.action_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('organization.leader.update_birth_date.diocese', 'Sửa ngày sinh huynh trưởng trong giáo phận', 'organization', 'leader', 'update_birth_date', 'diocese', 'Cho phép chỉ sửa ngày sinh của huynh trưởng thuộc giáo phận được phân quyền', TRUE, 740, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.leader.update_birth_date.deanery', 'Sửa ngày sinh huynh trưởng trong giáo hạt', 'organization', 'leader', 'update_birth_date', 'deanery', 'Cho phép chỉ sửa ngày sinh của huynh trưởng thuộc giáo hạt được phân quyền', TRUE, 740, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.leader.update_birth_date.parish', 'Sửa ngày sinh huynh trưởng trong giáo xứ', 'organization', 'leader', 'update_birth_date', 'parish', 'Cho phép chỉ sửa ngày sinh của huynh trưởng thuộc giáo xứ được phân quyền', TRUE, 740, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
