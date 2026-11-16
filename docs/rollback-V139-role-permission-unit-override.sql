-- Hoàn tác thủ công V139, V140, V141 (Flyway không dùng undo). Mất toàn bộ ngoại lệ phân quyền vai trò theo đơn vị.
-- Chạy trên đúng database, rồi triển khai lại bản BE trước V139.
BEGIN;
DROP VIEW IF EXISTS v_role_permission_listing;
DROP VIEW IF EXISTS v_account_effective_permission;
DROP VIEW IF EXISTS v_account_unit;

-- Dựng lại view quyền hiệu lực như V091.
CREATE VIEW v_account_effective_permission AS
SELECT ar.username, ar.role_code, p.permission_code, p.module_code, p.resource_code, p.action_code, p.scope_code,
       'ROLE' AS source_type, ar.expires_at AS account_role_expires_at, rp.expires_at AS permission_expires_at
FROM account_role ar
JOIN account a ON a.username = ar.username
JOIN role r ON r.role_code = ar.role_code
JOIN role_permission rp ON rp.role_code = ar.role_code
JOIN permission p ON p.permission_code = rp.permission_code
WHERE a.status = TRUE AND ar.status = TRUE AND r.status = TRUE AND rp.status = TRUE AND rp.revoked_at IS NULL AND p.status = TRUE
  AND (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
UNION ALL
SELECT apo.username, NULL, p.permission_code, p.module_code, p.resource_code, p.action_code, p.scope_code,
       'ACCOUNT_OVERRIDE', NULL, apo.expires_at
FROM account_permission_override apo
JOIN account a ON a.username = apo.username
JOIN permission p ON p.permission_code = apo.permission_code
WHERE a.status = TRUE AND apo.status = TRUE AND apo.revoked_at IS NULL AND p.status = TRUE
  AND (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP);

DROP TABLE IF EXISTS role_permission_unit_override;
-- V141 đã khôi phục các bản ghi role_permission mặc định; muốn quay về trạng thái thu hồi toàn hệ thống cũ, đặt lại revoked_at/revoked_by/revocation_reason
-- cho các dòng tương ứng từ bảng role_permission_unit_override (override_type = 'EXCLUDE') TRƯỚC khi xóa bảng này.
DELETE FROM flyway_schema_history WHERE version IN ('139', '140', '141');
COMMIT;
