-- Chấm điểm và duyệt chứng chỉ là nghiệp vụ theo scope, không thuộc SUPER_ADMIN.
UPDATE role_permission
SET status = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
WHERE role_code = 'SUPER_ADMIN'
  AND permission_code IN (
      'training.participation.score.diocese',
      'training.participation.score.deanery',
      'training.certificate.approve.diocese',
      'training.certificate.approve.deanery'
  )
  AND status = TRUE;

INSERT INTO role_permission (
    id, role_code, permission_code, effect, assigned_by, assigned_at,
    status, created_at, created_by, updated_at, updated_by
)
SELECT
    (
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 1, 8) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 9, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 13, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 17, 4) || '-' ||
        substr(md5(grant_row.role_code || ':' || grant_row.permission_code), 21, 12)
    )::uuid,
    grant_row.role_code,
    grant_row.permission_code,
    'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP,
    TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM (VALUES
    ('ADMIN_DIOCESE', 'training.participation.score.diocese'),
    ('ADMIN_DIOCESE', 'training.certificate.approve.diocese'),
    ('ADMIN_DEANERY', 'training.participation.score.deanery'),
    ('ADMIN_DEANERY', 'training.certificate.approve.deanery')
) AS grant_row(role_code, permission_code)
JOIN role r ON r.role_code = grant_row.role_code
JOIN permission p ON p.permission_code = grant_row.permission_code
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW',
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';
