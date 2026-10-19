-- ============================================================================
-- V061: Quyền gửi đăng ký tham dự khóa của vai trò giáo phận (chỉ khóa thuộc giáo phận).
-- ============================================================================

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.participation.submit.diocese', 'Submit registrations for diocese courses', 'training', 'participation', 'submit', 'diocese',
     'Add leaders in own diocese to diocese-owned training courses open for registration', TRUE, 205, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET
    permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO role_permission (
    id, role_code, permission_code, effect, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by
)
SELECT
    (
        substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 21, 12)
    )::uuid,
    r.role_code, p.permission_code, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role r
JOIN permission p ON p.permission_code = 'training.participation.submit.diocese'
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_DIOCESE')
AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_code = r.role_code AND rp.permission_code = p.permission_code
);
