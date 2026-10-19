-- ============================================================================
-- V064: Khóa/mở chỉnh sửa điểm của khóa huấn luyện.
--       Quyền training.participation.lock.{diocese,deanery} cho admin giáo phận/giáo hạt (khóa thuộc phạm vi của mình).
-- ============================================================================

ALTER TABLE training_course ADD COLUMN IF NOT EXISTS score_locked BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO permission_action (
    action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('lock', 'Khóa/mở', 'Khóa hoặc mở chỉnh sửa dữ liệu.', 95, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET action_name = EXCLUDED.action_name, description = EXCLUDED.description, status = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.participation.lock.diocese', 'Khóa/mở chỉnh sửa điểm khóa giáo phận', 'training', 'participation', 'lock', 'diocese',
     'Khóa hoặc mở chỉnh sửa điểm của các khóa huấn luyện thuộc giáo phận của mình', TRUE, 235, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.lock.deanery', 'Khóa/mở chỉnh sửa điểm khóa giáo hạt', 'training', 'participation', 'lock', 'deanery',
     'Khóa hoặc mở chỉnh sửa điểm của các khóa huấn luyện thuộc giáo hạt của mình', TRUE, 225, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
JOIN permission p ON p.permission_code IN ('training.participation.lock.diocese', 'training.participation.lock.deanery')
WHERE (
        r.role_code = 'SUPER_ADMIN'
        OR (r.role_code = 'ADMIN_DIOCESE' AND p.permission_code = 'training.participation.lock.diocese')
        OR (r.role_code = 'ADMIN_DEANERY' AND p.permission_code = 'training.participation.lock.deanery')
    )
AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_code = r.role_code AND rp.permission_code = p.permission_code
);
