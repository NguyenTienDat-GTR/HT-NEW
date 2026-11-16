-- Ngoại lệ phân quyền vai trò theo đơn vị: cấp cụ thể hơn thắng.
-- Ở V139 thu hồi riêng ở cấp trên luôn thắng mọi cấp phát riêng ở cấp dưới. Nay đơn vị có ngoại lệ ở cấp hẹp nhất trong chuỗi
-- giáo phận > giáo hạt > giáo xứ của tài khoản quyết định: GRANT = có quyền, EXCLUDE = không có; không có ngoại lệ nào thì theo mặc định.
-- Ví dụ thu hồi cả giáo phận rồi phân lại riêng cho một giáo hạt hoặc một giáo xứ thì đơn vị đó có quyền, các nơi khác vẫn không.
-- Chỉ thay định nghĩa view (cùng cột), không đổi dữ liệu.

CREATE OR REPLACE VIEW v_account_effective_permission AS
-- 1. Quyền mặc định của vai trò, trừ khi ngoại lệ cụ thể nhất trong chuỗi đơn vị của tài khoản là thu hồi riêng.
SELECT ar.username,
       ar.role_code,
       p.permission_code,
       p.module_code,
       p.resource_code,
       p.action_code,
       p.scope_code,
       'ROLE'                       AS source_type,
       ar.expires_at                AS account_role_expires_at,
       rp.expires_at                AS permission_expires_at,
       'DEFAULT'::VARCHAR(20)       AS grant_origin,
       NULL::VARCHAR(20)            AS scope_level,
       NULL::UUID                   AS unit_id,
       rp.assigned_by               AS assigned_by,
       rp.assigned_at               AS assigned_at,
       rp.conditions                AS note
FROM account_role ar
         JOIN account a ON a.username = ar.username
         JOIN role r ON r.role_code = ar.role_code
         JOIN role_permission rp ON rp.role_code = ar.role_code
         JOIN permission p ON p.permission_code = rp.permission_code
         JOIN v_account_unit au ON au.username = ar.username
WHERE a.status = TRUE
  AND ar.status = TRUE
  AND r.status = TRUE
  AND rp.status = TRUE
  AND rp.revoked_at IS NULL
  AND p.status = TRUE
  AND (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
  AND NOT EXISTS (SELECT 1
                  FROM role_permission_unit_override x
                  WHERE x.role_code = rp.role_code
                    AND x.permission_code = rp.permission_code
                    AND x.override_type = 'EXCLUDE'
                    AND x.status = TRUE
                    AND x.revoked_at IS NULL
                    AND x.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
                    -- không có cấp phát riêng nào ở cấp hẹp hơn thu hồi này
                    AND NOT EXISTS (SELECT 1
                                    FROM role_permission_unit_override g
                                    WHERE g.role_code = x.role_code
                                      AND g.permission_code = x.permission_code
                                      AND g.override_type = 'GRANT'
                                      AND g.status = TRUE
                                      AND g.revoked_at IS NULL
                                      AND (g.expires_at IS NULL OR g.expires_at > CURRENT_TIMESTAMP)
                                      AND g.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
                                      AND (CASE g.scope_level WHEN 'DIOCESE' THEN 1 WHEN 'DEANERY' THEN 2 ELSE 3 END)
                                          > (CASE x.scope_level WHEN 'DIOCESE' THEN 1 WHEN 'DEANERY' THEN 2 ELSE 3 END)))

UNION ALL

-- 2. Quyền cấp riêng cho vai trò tại đơn vị: chỉ khi mặc định chưa có quyền đó và không có thu hồi riêng ở cấp hẹp hơn.
SELECT ar.username,
       ar.role_code,
       p.permission_code,
       p.module_code,
       p.resource_code,
       p.action_code,
       p.scope_code,
       'ROLE',
       ar.expires_at,
       o.expires_at,
       'UNIT',
       o.scope_level,
       o.unit_id,
       o.assigned_by,
       o.assigned_at,
       o.conditions
FROM account_role ar
         JOIN account a ON a.username = ar.username
         JOIN role r ON r.role_code = ar.role_code
         JOIN v_account_unit au ON au.username = ar.username
         JOIN role_permission_unit_override o ON o.role_code = ar.role_code
         JOIN permission p ON p.permission_code = o.permission_code
WHERE a.status = TRUE
  AND ar.status = TRUE
  AND r.status = TRUE
  AND p.status = TRUE
  AND o.override_type = 'GRANT'
  AND o.status = TRUE
  AND o.revoked_at IS NULL
  AND o.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
  AND (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND (o.expires_at IS NULL OR o.expires_at > CURRENT_TIMESTAMP)
  AND NOT EXISTS (SELECT 1
                  FROM role_permission_unit_override x
                  WHERE x.role_code = o.role_code
                    AND x.permission_code = o.permission_code
                    AND x.override_type = 'EXCLUDE'
                    AND x.status = TRUE
                    AND x.revoked_at IS NULL
                    AND x.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
                    AND (CASE x.scope_level WHEN 'DIOCESE' THEN 1 WHEN 'DEANERY' THEN 2 ELSE 3 END)
                        > (CASE o.scope_level WHEN 'DIOCESE' THEN 1 WHEN 'DEANERY' THEN 2 ELSE 3 END))
  AND NOT EXISTS (SELECT 1
                  FROM role_permission rp
                  WHERE rp.role_code = o.role_code
                    AND rp.permission_code = o.permission_code
                    AND rp.status = TRUE
                    AND rp.revoked_at IS NULL
                    AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
                    -- mặc định chỉ được tính là "đã có" khi không bị thu hồi riêng thắng cấp cấp phát này
                    AND NOT EXISTS (SELECT 1
                                    FROM role_permission_unit_override x2
                                    WHERE x2.role_code = rp.role_code
                                      AND x2.permission_code = rp.permission_code
                                      AND x2.override_type = 'EXCLUDE'
                                      AND x2.status = TRUE
                                      AND x2.revoked_at IS NULL
                                      AND x2.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
                                      AND NOT EXISTS (SELECT 1
                                                      FROM role_permission_unit_override g2
                                                      WHERE g2.role_code = x2.role_code
                                                        AND g2.permission_code = x2.permission_code
                                                        AND g2.override_type = 'GRANT'
                                                        AND g2.status = TRUE
                                                        AND g2.revoked_at IS NULL
                                                        AND (g2.expires_at IS NULL OR g2.expires_at > CURRENT_TIMESTAMP)
                                                        AND g2.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
                                                        AND (CASE g2.scope_level WHEN 'DIOCESE' THEN 1 WHEN 'DEANERY' THEN 2 ELSE 3 END)
                                                            > (CASE x2.scope_level WHEN 'DIOCESE' THEN 1 WHEN 'DEANERY' THEN 2 ELSE 3 END))))
  AND NOT EXISTS (SELECT 1
                  FROM role_permission_unit_override o2
                  WHERE o2.role_code = o.role_code
                    AND o2.permission_code = o.permission_code
                    AND o2.override_type = 'GRANT'
                    AND o2.status = TRUE
                    AND o2.revoked_at IS NULL
                    AND (o2.expires_at IS NULL OR o2.expires_at > CURRENT_TIMESTAMP)
                    AND o2.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id)
                    AND o2.id < o.id)

UNION ALL

-- 3. Quyền cấp riêng cho từng tài khoản (như V091).
SELECT apo.username,
       NULL,
       p.permission_code,
       p.module_code,
       p.resource_code,
       p.action_code,
       p.scope_code,
       'ACCOUNT_OVERRIDE',
       NULL,
       apo.expires_at,
       'ACCOUNT',
       NULL,
       NULL,
       apo.created_by,
       apo.created_at,
       apo.reason
FROM account_permission_override apo
         JOIN account a ON a.username = apo.username
         JOIN permission p ON p.permission_code = apo.permission_code
WHERE a.status = TRUE
  AND apo.status = TRUE
  AND apo.revoked_at IS NULL
  AND p.status = TRUE
  AND (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP);
