-- =============================================================================
-- V2__professional_rbac_permission_matrix.sql
-- =============================================================================

-- =============================================================================
-- 1. UNIQUE CONSTRAINT ROLE
-- =============================================================================

DO $$
BEGIN
IF NOT EXISTS (
SELECT 1
FROM pg_constraint
WHERE conname = 'uq_role_name'
) THEN
ALTER TABLE role
    ADD CONSTRAINT uq_role_name UNIQUE (role_name);
END IF;
END
$$;

-- =============================================================================
-- 2. PERMISSION TAXONOMY DATA
-- =============================================================================

INSERT INTO permission_module (
    module_code,
    module_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES (
           'system',
           'System',
           'Default module for legacy permissions',
           999,
           TRUE,
           CURRENT_TIMESTAMP,
           'SYSTEM',
           CURRENT_TIMESTAMP,
           'SYSTEM'
       )
    ON CONFLICT (module_code) DO NOTHING;

INSERT INTO permission_action (
    action_code,
    action_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES
    ('create','Create','Create new records',10,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('read','Read','View records and details',20,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('update','Update','Edit existing records',30,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('delete','Delete','Delete records',40,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('approve','Approve','Approve business workflows',50,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('export','Export','Export records and reports',60,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('manage','Manage','Full management capability for a resource',90,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM')
    ON CONFLICT (action_code) DO NOTHING;

INSERT INTO permission_scope (
    scope_code,
    scope_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES
    ('own','Own','Only records owned by current account',10,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('parish','Parish','Records within current parish',20,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('deanery','Deanery','Records within current deanery',30,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('diocese','Diocese','Records within current diocese',40,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
    ('all','All','All records in system',90,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM')
    ON CONFLICT (scope_code) DO NOTHING;

-- =============================================================================
-- 3. CREATE RESOURCES FROM LEGACY PERMISSIONS
-- =============================================================================

INSERT INTO permission_resource (
    resource_code,
    module_code,
    resource_name,
    description,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT DISTINCT
    lower(regexp_replace(permission_code,'[^a-zA-Z0-9]+','_','g')),
    'system',
    COALESCE(permission_name,permission_code),
    'Legacy permission converted to RBAC resource',
    999,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM permission
WHERE permission_code IS NOT NULL
    ON CONFLICT (resource_code) DO NOTHING;

-- =============================================================================
-- 4. UPDATE LEGACY PERMISSIONS
-- =============================================================================

UPDATE permission
SET
    module_code = COALESCE(module_code,'system'),
    resource_code = COALESCE(
            resource_code,
            lower(regexp_replace(permission_code,'[^a-zA-Z0-9]+','_','g'))
                    ),
    action_code = COALESCE(action_code,'manage'),
    scope_code = COALESCE(scope_code,'all'),
    updated_at = CURRENT_TIMESTAMP,
    updated_by = COALESCE(updated_by,'SYSTEM')
WHERE
    module_code IS NULL
   OR resource_code IS NULL
   OR action_code IS NULL
   OR scope_code IS NULL;

-- =============================================================================
-- 5. ROLE_PERMISSION CONSTRAINTS
-- =============================================================================

DO $$
BEGIN
IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname='chk_role_permission_effect'
) THEN
    ALTER TABLE role_permission
        ADD CONSTRAINT chk_role_permission_effect
        CHECK (effect IN ('ALLOW','DENY'));
END IF;

IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname='chk_role_permission_expires_at'
) THEN
    ALTER TABLE role_permission
        ADD CONSTRAINT chk_role_permission_expires_at
        CHECK (
            expires_at IS NULL
            OR expires_at > assigned_at
        );
END IF;

END
$$;

-- =============================================================================
-- 6. ACCOUNT OVERRIDE CONSTRAINTS
-- =============================================================================

DO $$
BEGIN
IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname='chk_account_permission_override_effect'
) THEN
    ALTER TABLE account_permission_override
        ADD CONSTRAINT chk_account_permission_override_effect
        CHECK (effect IN ('ALLOW','DENY'));
END IF;

IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname='chk_account_permission_override_expires_at'
) THEN
    ALTER TABLE account_permission_override
        ADD CONSTRAINT chk_account_permission_override_expires_at
        CHECK (
            expires_at IS NULL
            OR expires_at > created_at
        );
END IF;

END
$$;

-- =============================================================================
-- 7. PRIMARY ROLE INDEX
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_one_primary_role
    ON account_role(username)
    WHERE is_primary = TRUE;

-- =============================================================================
-- 8. ROLE PERMISSION MATRIX VIEW
-- =============================================================================

CREATE OR REPLACE VIEW v_role_permission_matrix AS
SELECT
    r.role_code,
    r.role_name,
    pm.module_code,
    pm.module_name,
    pr.resource_code,
    pr.resource_name,
    pa.action_code,
    pa.action_name,
    ps.scope_code,
    ps.scope_name,
    p.permission_code,
    p.permission_name,
    rp.effect,
    rp.conditions,
    rp.expires_at,
    (
        rp.expires_at IS NULL
            OR rp.expires_at > CURRENT_TIMESTAMP
        ) AS is_active
FROM role r
         JOIN role_permission rp
              ON rp.role_code = r.role_code
         JOIN permission p
              ON p.permission_code = rp.permission_code
         JOIN permission_module pm
              ON pm.module_code = p.module_code
         JOIN permission_resource pr
              ON pr.resource_code = p.resource_code
                  AND pr.module_code = p.module_code
         JOIN permission_action pa
              ON pa.action_code = p.action_code
         JOIN permission_scope ps
              ON ps.scope_code = p.scope_code
WHERE
    r.status = TRUE
  AND p.status = TRUE;

-- =============================================================================
-- 9. EFFECTIVE PERMISSIONS VIEW
-- =============================================================================

CREATE OR REPLACE VIEW v_account_effective_permission AS

SELECT
    ar.username,
    ar.role_code,
    p.permission_code,
    p.module_code,
    p.resource_code,
    p.action_code,
    p.scope_code,
    rp.effect,
    'ROLE' AS source_type,
    ar.expires_at AS account_role_expires_at,
    rp.expires_at AS permission_expires_at
FROM account_role ar
         JOIN role_permission rp
              ON rp.role_code = ar.role_code
         JOIN permission p
              ON p.permission_code = rp.permission_code
WHERE
    (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
  AND p.status = TRUE

UNION ALL

SELECT
    apo.username,
    NULL,
    p.permission_code,
    p.module_code,
    p.resource_code,
    p.action_code,
    p.scope_code,
    apo.effect,
    'ACCOUNT_OVERRIDE',
    NULL,
    apo.expires_at
FROM account_permission_override apo
         JOIN permission p
              ON p.permission_code = apo.permission_code
WHERE
    (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP)
  AND p.status = TRUE;

-- =============================================================================
-- 10. INDEXES
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_permission_matrix
    ON permission(module_code, resource_code, action_code, scope_code);

CREATE INDEX IF NOT EXISTS idx_permission_resource_module
    ON permission_resource(module_code, display_order);

CREATE INDEX IF NOT EXISTS idx_permission_action_display
    ON permission_action(display_order, status);

CREATE INDEX IF NOT EXISTS idx_permission_scope_display
    ON permission_scope(display_order, status);

CREATE INDEX IF NOT EXISTS idx_permission_module_display
    ON permission_module(display_order, status);

CREATE INDEX IF NOT EXISTS idx_role_permission_permission
    ON role_permission(permission_code);

CREATE INDEX IF NOT EXISTS idx_role_permission_active
    ON role_permission(role_code, permission_code, effect)
    WHERE expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_role_permission_expires
    ON role_permission(expires_at)
    WHERE expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_account_role_username
    ON account_role(username);

CREATE INDEX IF NOT EXISTS idx_account_role_role
    ON account_role(role_code);

CREATE INDEX IF NOT EXISTS idx_account_role_primary
    ON account_role(username, is_primary)
    WHERE is_primary = TRUE;

CREATE INDEX IF NOT EXISTS idx_account_permission_override_username
    ON account_permission_override(username);

CREATE INDEX IF NOT EXISTS idx_account_permission_override_permission
    ON account_permission_override(permission_code);

CREATE INDEX IF NOT EXISTS idx_account_permission_override_active
    ON account_permission_override(username, effect)
    WHERE expires_at IS NULL;

-- =============================================================================
-- END
-- =============================================================================