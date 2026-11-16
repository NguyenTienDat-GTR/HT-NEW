-- Phân quyền vai trò theo từng đơn vị (giáo phận / giáo hạt / giáo xứ).
-- role_permission giữ nguyên là bộ quyền mặc định dùng chung; ngoại lệ theo đơn vị nằm ở bảng riêng:
--   GRANT   = cấp thêm một quyền riêng cho vai trò tại đơn vị (và các đơn vị cấp dưới);
--   EXCLUDE = thu hồi riêng một quyền mặc định của vai trò tại đơn vị (và các đơn vị cấp dưới).
-- Quyền hiệu lực = (mặc định + GRANT trong chuỗi giáo phận/giáo hạt/giáo xứ của tài khoản) - EXCLUDE trong chuỗi đó.
-- Không sửa, không xóa dữ liệu của bảng cũ; khi bảng ngoại lệ còn trống thì view trả đúng các dòng như trước.

CREATE TABLE role_permission_unit_override
(
    id                VARCHAR(255) PRIMARY KEY,
    role_code         VARCHAR(255) NOT NULL,
    permission_code   VARCHAR(255) NOT NULL,
    scope_level       VARCHAR(20)  NOT NULL,
    diocese_id        UUID,
    deanery_id        UUID,
    parish_id         UUID,
    unit_id           UUID GENERATED ALWAYS AS (COALESCE(parish_id, deanery_id, diocese_id)) STORED,
    override_type     VARCHAR(20)  NOT NULL,
    conditions        TEXT,
    assigned_by       VARCHAR(255),
    assigned_at       TIMESTAMP WITH TIME ZONE,
    expires_at        TIMESTAMP WITH TIME ZONE,
    revoked_at        TIMESTAMP WITH TIME ZONE,
    revoked_by        VARCHAR(100),
    revocation_reason VARCHAR(20),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by        VARCHAR(100),
    updated_at        TIMESTAMP WITH TIME ZONE,
    updated_by        VARCHAR(100),
    status            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_rpuo_role FOREIGN KEY (role_code) REFERENCES role (role_code),
    CONSTRAINT fk_rpuo_permission FOREIGN KEY (permission_code) REFERENCES permission (permission_code),
    CONSTRAINT fk_rpuo_diocese FOREIGN KEY (diocese_id) REFERENCES diocese (id) ON DELETE CASCADE,
    CONSTRAINT fk_rpuo_deanery FOREIGN KEY (deanery_id) REFERENCES deanery (id) ON DELETE CASCADE,
    CONSTRAINT fk_rpuo_parish FOREIGN KEY (parish_id) REFERENCES parish (id) ON DELETE CASCADE,
    CONSTRAINT uk_rpuo UNIQUE (role_code, permission_code, unit_id, override_type),
    CONSTRAINT chk_rpuo_type CHECK (override_type IN ('GRANT', 'EXCLUDE')),
    CONSTRAINT chk_rpuo_level CHECK (
        (scope_level = 'DIOCESE' AND diocese_id IS NOT NULL AND deanery_id IS NULL AND parish_id IS NULL)
        OR (scope_level = 'DEANERY' AND deanery_id IS NOT NULL AND diocese_id IS NULL AND parish_id IS NULL)
        OR (scope_level = 'PARISH' AND parish_id IS NOT NULL AND diocese_id IS NULL AND deanery_id IS NULL)
    ),
    CONSTRAINT chk_rpuo_expiry CHECK (override_type = 'GRANT' OR expires_at IS NULL)
);

CREATE INDEX idx_rpuo_role_unit ON role_permission_unit_override (role_code, unit_id);
CREATE INDEX idx_rpuo_unit ON role_permission_unit_override (unit_id);
CREATE INDEX idx_rpuo_expires ON role_permission_unit_override (expires_at)
    WHERE revoked_at IS NULL AND expires_at IS NOT NULL;

-- Chuỗi đơn vị của từng tài khoản, cùng thứ tự suy ra như AccountRepository.findActive{Diocese,Deanery,Parish}IdByUsername
-- nhưng không lọc trạng thái đơn vị: đơn vị bị khóa đã được xử lý riêng ở lúc đăng nhập, ngoại lệ không được biến mất theo.
CREATE VIEW v_account_unit AS
SELECT a.username,
       COALESCE(a.diocese_id, ade.diocese_id, apde.diocese_id, lde.diocese_id) AS diocese_id,
       COALESCE(a.deanery_id, apde.id, lde.id)                                 AS deanery_id,
       COALESCE(a.parish_id, lp.id)                                            AS parish_id
FROM account a
         LEFT JOIN deanery ade ON ade.id = a.deanery_id
         LEFT JOIN parish ap ON ap.id = a.parish_id
         LEFT JOIN deanery apde ON apde.id = ap.deanery_id
         LEFT JOIN leader l ON l.id = a.leader_id
         LEFT JOIN parish lp ON lp.id = l.parish_id
         LEFT JOIN deanery lde ON lde.id = lp.deanery_id;

DROP VIEW IF EXISTS v_account_effective_permission;

CREATE VIEW v_account_effective_permission AS
-- 1. Quyền mặc định của vai trò, trừ quyền bị thu hồi riêng ở một đơn vị trong chuỗi của tài khoản.
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
                    AND x.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id))

UNION ALL

-- 2. Quyền cấp riêng cho vai trò tại đơn vị: chỉ khi mặc định chưa có quyền đó và không bị thu hồi riêng ở cấp nào trong chuỗi.
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
                    AND x.unit_id IN (au.diocese_id, au.deanery_id, au.parish_id))
  AND NOT EXISTS (SELECT 1
                  FROM role_permission rp
                  WHERE rp.role_code = o.role_code
                    AND rp.permission_code = o.permission_code
                    AND rp.status = TRUE
                    AND rp.revoked_at IS NULL
                    AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP))
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

-- Danh sách hiển thị cho màn "Phân quyền vai trò": dòng mặc định và dòng ngoại lệ theo đơn vị
-- (dòng thu hồi riêng đã gỡ thì ẩn). Mỗi dòng ngoại lệ mang đủ chuỗi giáo phận/giáo hạt/giáo xứ để lọc.
CREATE VIEW v_role_permission_listing AS
SELECT rp.id,
       rp.role_code,
       rp.permission_code,
       'DEFAULT'::VARCHAR(20)  AS entry_type,
       NULL::VARCHAR(20)       AS scope_level,
       NULL::UUID              AS diocese_id,
       NULL::UUID              AS deanery_id,
       NULL::UUID              AS parish_id,
       rp.conditions,
       rp.assigned_by,
       rp.assigned_at,
       rp.expires_at,
       rp.revoked_at,
       rp.revoked_by,
       rp.revocation_reason,
       rp.status,
       rp.created_at,
       rp.created_by,
       rp.updated_at,
       rp.updated_by
FROM role_permission rp

UNION ALL

SELECT o.id,
       o.role_code,
       o.permission_code,
       CASE o.override_type WHEN 'GRANT' THEN 'UNIT_GRANT' ELSE 'UNIT_EXCLUDE' END,
       o.scope_level,
       COALESCE(o.diocese_id, de.diocese_id, pde.diocese_id),
       COALESCE(o.deanery_id, pa.deanery_id),
       o.parish_id,
       o.conditions,
       o.assigned_by,
       o.assigned_at,
       o.expires_at,
       o.revoked_at,
       o.revoked_by,
       o.revocation_reason,
       o.status,
       o.created_at,
       o.created_by,
       o.updated_at,
       o.updated_by
FROM role_permission_unit_override o
         LEFT JOIN deanery de ON de.id = o.deanery_id
         LEFT JOIN parish pa ON pa.id = o.parish_id
         LEFT JOIN deanery pde ON pde.id = pa.deanery_id
WHERE o.override_type = 'GRANT'
   OR o.revoked_at IS NULL;
