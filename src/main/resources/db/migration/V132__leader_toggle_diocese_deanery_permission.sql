-- Thêm quyền đổi trạng thái hồ sơ huynh trưởng cho phạm vi giáo phận và giáo hạt (giống phạm vi giáo xứ hiện có). Chỉ khai báo quyền; không gán cho vai trò nào.
INSERT INTO permission (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
    ('organization.leader.toggle.diocese', 'Đổi trạng thái hồ sơ huynh trưởng trong giáo phận', 'organization', 'leader', 'toggle', 'diocese', 'Cho phép đổi trạng thái hồ sơ huynh trưởng thuộc giáo phận được phân quyền', TRUE, 743, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.leader.toggle.deanery', 'Đổi trạng thái hồ sơ huynh trưởng trong giáo hạt', 'organization', 'leader', 'toggle', 'deanery', 'Cho phép đổi trạng thái hồ sơ huynh trưởng thuộc giáo hạt được phân quyền', TRUE, 744, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
