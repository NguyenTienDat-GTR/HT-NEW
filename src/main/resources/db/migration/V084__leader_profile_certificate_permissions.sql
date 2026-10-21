-- Quyền xem chứng nhận của huynh trưởng ngay trong hồ sơ huynh trưởng (không mở menu Chứng nhận):
-- admin giáo hạt / giáo xứ xem được chứng nhận của huynh trưởng thuộc phạm vi quản lý, bất kể chứng nhận thuộc khóa cấp nào.
INSERT INTO permission_resource (resource_code, module_code, created_at, created_by, updated_at, updated_by, status, resource_name, description, display_order)
SELECT 'leader_certificate', 'organization', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE,
       'Chứng nhận trong hồ sơ huynh trưởng',
       'Xem chứng nhận của huynh trưởng trong hồ sơ huynh trưởng, không bao gồm menu Chứng nhận.',
       COALESCE((SELECT max(display_order) FROM permission_resource), 0) + 1
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE resource_code = 'leader_certificate');

INSERT INTO permission (permission_code, module_code, resource_code, action_code, scope_code, created_at, created_by, updated_at, updated_by, status, permission_name, description, is_system, display_order)
SELECT v.permission_code, 'organization', 'leader_certificate', 'read', v.scope_code,
       CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE,
       v.permission_name, v.description, TRUE,
       (SELECT COALESCE(max(display_order), 0) FROM permission) + v.step
FROM (VALUES
    ('organization.leader_certificate.read.deanery', 'deanery', 'Xem chứng nhận trong hồ sơ huynh trưởng - Giáo hạt',
     'Cho phép xem mọi chứng nhận (kể cả đã thu hồi) của huynh trưởng thuộc giáo hạt gắn với tài khoản, ngay trong hồ sơ huynh trưởng.', 10),
    ('organization.leader_certificate.read.parish', 'parish', 'Xem chứng nhận trong hồ sơ huynh trưởng - Giáo xứ',
     'Cho phép xem mọi chứng nhận (kể cả đã thu hồi) của huynh trưởng thuộc giáo xứ gắn với tài khoản, ngay trong hồ sơ huynh trưởng.', 20)
) AS v(permission_code, scope_code, permission_name, description, step)
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, effect, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, v.role_code, v.permission_code, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('ADMIN_DEANERY', 'organization.leader_certificate.read.deanery'),
    ('ADMIN_PARISH', 'organization.leader_certificate.read.parish')
) AS v(role_code, permission_code)
WHERE EXISTS (SELECT 1 FROM role r WHERE r.role_code = v.role_code)
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_code = v.role_code AND rp.permission_code = v.permission_code);
