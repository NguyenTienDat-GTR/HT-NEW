-- ============================================================================
-- V075:
--   - Cấp độ bằng nhau: nhiều cấp có thể cùng display_order trong một giáo phận (cùng bậc).
--   - Thu hồi chứng nhận có nguồn (MANUAL | COURSE_DISABLED): chứng nhận thu hồi do khóa bị vô hiệu hóa
--     được mở lại khi bật lại khóa; thu hồi vì lý do khác thì không.
--   - Cấp lại chứng nhận đã thu hồi (quyền training.certificate.reissue.*): lưu người cấp lại, ngày, lý do.
-- ============================================================================

ALTER TABLE leader_level DROP CONSTRAINT IF EXISTS uk_leader_level_order;

ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS revocation_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS reissued_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reissued_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reissue_reason TEXT;

-- Chứng nhận đã bị thu hồi trước đây đều là thu hồi thủ công.
UPDATE certificate SET revocation_source = 'MANUAL' WHERE approval_status = 'REVOKED' AND revocation_source IS NULL;

CREATE INDEX IF NOT EXISTS idx_certificate_course_status ON certificate (course_code, approval_status);

INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('reissue', 'Cấp lại', 'Cấp lại bản ghi đã bị thu hồi.', 98, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET action_name = EXCLUDED.action_name, description = EXCLUDED.description, status = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.certificate.reissue.diocese', 'Cấp lại chứng nhận giáo phận', 'training', 'certificate', 'reissue', 'diocese',
     'Cấp lại chứng nhận đã bị thu hồi của các khóa thuộc giáo phận của mình', TRUE, 248, TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.reissue.deanery', 'Cấp lại chứng nhận giáo hạt', 'training', 'certificate', 'reissue', 'deanery',
     'Cấp lại chứng nhận đã bị thu hồi của các khóa thuộc giáo hạt của mình', TRUE, 238, TRUE,
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
JOIN permission p ON p.permission_code IN ('training.certificate.reissue.diocese', 'training.certificate.reissue.deanery')
WHERE (r.role_code = 'ADMIN_DIOCESE' AND p.scope_code = 'diocese')
   OR (r.role_code = 'ADMIN_DEANERY' AND p.scope_code = 'deanery')
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW', expires_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
