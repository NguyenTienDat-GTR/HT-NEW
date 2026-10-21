-- ============================================================================
-- V071: Thu hồi chứng nhận; bỏ tạo / sửa / bật tắt chứng nhận thủ công.
--       - Chứng nhận chỉ được sinh tự động khi chấm điểm đạt của khóa huấn luyện.
--       - Xóa các quyền training.certificate.{create,update,delete,toggle}.* và các gán quyền liên quan.
--       - Thêm quyền training.certificate.revoke.{diocese,deanery} cho admin giáo phận / giáo hạt.
-- ============================================================================

ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS revocation_reason TEXT;

-- Xóa quyền cũ (gán cho vai trò / tài khoản trước, rồi tới bản ghi quyền).
DELETE FROM role_permission
WHERE permission_code IN (
    SELECT permission_code FROM permission
    WHERE resource_code = 'certificate' AND action_code IN ('create', 'update', 'delete', 'toggle')
);

DELETE FROM account_permission_override
WHERE permission_code IN (
    SELECT permission_code FROM permission
    WHERE resource_code = 'certificate' AND action_code IN ('create', 'update', 'delete', 'toggle')
);

DELETE FROM permission
WHERE resource_code = 'certificate' AND action_code IN ('create', 'update', 'delete', 'toggle');

-- Quyền thu hồi chứng nhận theo phạm vi.
INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('revoke', 'Thu hồi', 'Thu hồi bản ghi đã duyệt.', 97, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET action_name = EXCLUDED.action_name, description = EXCLUDED.description, status = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.certificate.revoke.diocese', 'Thu hồi chứng nhận giáo phận', 'training', 'certificate', 'revoke', 'diocese',
     'Thu hồi chứng nhận đã duyệt của các khóa thuộc giáo phận của mình', TRUE, 247, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.revoke.deanery', 'Thu hồi chứng nhận giáo hạt', 'training', 'certificate', 'revoke', 'deanery',
     'Thu hồi chứng nhận đã duyệt của các khóa thuộc giáo hạt của mình', TRUE, 237, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, status = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

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
JOIN permission p ON p.permission_code IN ('training.certificate.revoke.diocese', 'training.certificate.revoke.deanery')
WHERE (r.role_code = 'ADMIN_DIOCESE' AND p.scope_code = 'diocese')
   OR (r.role_code = 'ADMIN_DEANERY' AND p.scope_code = 'deanery')
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW', expires_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
