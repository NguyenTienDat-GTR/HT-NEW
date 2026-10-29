-- Thêm quyền chỉnh sửa huynh trưởng cho phạm vi giáo phận và giáo hạt. Chỉ khai báo quyền; chưa gán cho vai trò nào và chưa áp dụng trong code.
INSERT INTO permission (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('organization.leader.update.diocese', 'Chỉnh sửa huynh trưởng trong giáo phận', 'organization', 'leader', 'update', 'diocese', 'Cho phép chỉnh sửa huynh trưởng thuộc giáo phận được phân quyền', TRUE, 739, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.leader.update.deanery', 'Chỉnh sửa huynh trưởng trong giáo hạt', 'organization', 'leader', 'update', 'deanery', 'Cho phép chỉnh sửa huynh trưởng thuộc giáo hạt được phân quyền', TRUE, 740, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
