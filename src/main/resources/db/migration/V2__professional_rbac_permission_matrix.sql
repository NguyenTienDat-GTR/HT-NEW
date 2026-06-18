-- =============================================================================
-- RBAC PERMISSION MATRIX UPGRADE
-- =============================================================================
-- Goals:
-- - Model permissions as module/resource/action/scope for a maintainable matrix.
-- - Allow each account to have multiple roles.
-- - Keep role permissions explicit and auditable.
-- - Support rare per-account allow/deny overrides without changing role design.

-- -----------------------------------------------------------------------------
-- 1. Role metadata
-- -----------------------------------------------------------------------------
ALTER TABLE role
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS status BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE role
    ADD CONSTRAINT uq_role_name UNIQUE (role_name);

-- -----------------------------------------------------------------------------
-- 2. Permission taxonomy dictionaries
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS permission_module (
    module_code VARCHAR(50) PRIMARY KEY,
    module_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permission_resource (
    resource_code VARCHAR(80) PRIMARY KEY,
    module_code VARCHAR(50) NOT NULL,
    resource_name VARCHAR(120) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_permission_resource_module
        FOREIGN KEY (module_code) REFERENCES permission_module(module_code) ON DELETE RESTRICT,
    CONSTRAINT uq_permission_resource_module_name UNIQUE (module_code, resource_name)
);

CREATE TABLE IF NOT EXISTS permission_action (
    action_code VARCHAR(50) PRIMARY KEY,
    action_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permission_scope (
    scope_code VARCHAR(50) PRIMARY KEY,
    scope_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    status BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 3. Backfill taxonomy from existing flat permissions
-- -----------------------------------------------------------------------------
INSERT INTO permission_module (module_code, module_name, description, display_order)
VALUES ('system', 'System', 'Default module for legacy permissions', 999)
ON CONFLICT (module_code) DO NOTHING;

INSERT INTO permission_action (action_code, action_name, description, display_order)
VALUES
    ('create', 'Create', 'Create new records', 10),
    ('read', 'Read', 'View records and details', 20),
    ('update', 'Update', 'Edit existing records', 30),
    ('delete', 'Delete', 'Delete records', 40),
    ('approve', 'Approve', 'Approve business workflows', 50),
    ('export', 'Export', 'Export records and reports', 60),
    ('manage', 'Manage', 'Full management capability for a resource', 90)
ON CONFLICT (action_code) DO NOTHING;

INSERT INTO permission_scope (scope_code, scope_name, description, display_order)
VALUES
    ('own', 'Own', 'Only records owned by the current account', 10),
    ('parish', 'Parish', 'Records within the current parish', 20),
    ('deanery', 'Deanery', 'Records within the current deanery', 30),
    ('diocese', 'Diocese', 'Records within the current diocese', 40),
    ('all', 'All', 'All records in the system', 90)
ON CONFLICT (scope_code) DO NOTHING;

INSERT INTO permission_resource (resource_code, module_code, resource_name, description, display_order)
SELECT DISTINCT
    lower(regexp_replace(permission_code, '[^a-zA-Z0-9]+', '_', 'g')) AS resource_code,
    'system',
    permission_name,
    'Legacy permission converted to RBAC resource',
    999
FROM permission
ON CONFLICT (resource_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Permission metadata for matrix rendering and enforcement
-- -----------------------------------------------------------------------------
ALTER TABLE permission
    ADD COLUMN IF NOT EXISTS module_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS resource_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS action_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS scope_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS status BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE permission
SET
    module_code = COALESCE(module_code, 'system'),
    resource_code = COALESCE(resource_code, lower(regexp_replace(permission_code, '[^a-zA-Z0-9]+', '_', 'g'))),
    action_code = COALESCE(action_code, 'manage'),
    scope_code = COALESCE(scope_code, 'all')
WHERE module_code IS NULL
   OR resource_code IS NULL
   OR action_code IS NULL
   OR scope_code IS NULL;

ALTER TABLE permission
    ALTER COLUMN module_code SET NOT NULL,
    ALTER COLUMN resource_code SET NOT NULL,
    ALTER COLUMN action_code SET NOT NULL,
    ALTER COLUMN scope_code SET NOT NULL;

ALTER TABLE permission
    ADD CONSTRAINT fk_permission_module
        FOREIGN KEY (module_code) REFERENCES permission_module(module_code) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_permission_resource
        FOREIGN KEY (resource_code) REFERENCES permission_resource(resource_code) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_permission_action
        FOREIGN KEY (action_code) REFERENCES permission_action(action_code) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_permission_scope
        FOREIGN KEY (scope_code) REFERENCES permission_scope(scope_code) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_permission_matrix_cell
        UNIQUE (module_code, resource_code, action_code, scope_code);

-- -----------------------------------------------------------------------------
-- 5. Rename role-permission join and add grant metadata
-- -----------------------------------------------------------------------------
ALTER TABLE IF EXISTS permission_role RENAME TO role_permission;

ALTER TABLE role_permission
    ADD COLUMN IF NOT EXISTS effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
    ADD COLUMN IF NOT EXISTS conditions JSONB,
    ADD COLUMN IF NOT EXISTS assigned_by VARCHAR(50),
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT chk_role_permission_effect CHECK (effect IN ('ALLOW', 'DENY')),
    ADD CONSTRAINT chk_role_permission_expires_at CHECK (expires_at IS NULL OR expires_at > assigned_at);

-- -----------------------------------------------------------------------------
-- 6. Multiple roles per account
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS account_role (
    username VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_by VARCHAR(50),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (username, role_code),
    CONSTRAINT fk_account_role_account
        FOREIGN KEY (username) REFERENCES account(username) ON DELETE CASCADE,
    CONSTRAINT fk_account_role_role
        FOREIGN KEY (role_code) REFERENCES role(role_code) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES account(username) ON DELETE SET NULL,
    CONSTRAINT chk_account_role_expires_at CHECK (expires_at IS NULL OR expires_at > assigned_at)
);

INSERT INTO account_role (username, role_code, is_primary)
SELECT username, role_code, TRUE
FROM account
WHERE role_code IS NOT NULL
ON CONFLICT (username, role_code) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_one_primary_role
    ON account_role(username)
    WHERE is_primary;

ALTER TABLE account
    DROP CONSTRAINT IF EXISTS fk_account_role,
    DROP COLUMN IF EXISTS role_code;

-- -----------------------------------------------------------------------------
-- 7. Optional direct account permission overrides
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS account_permission_override (
    username VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    effect VARCHAR(10) NOT NULL,
    reason TEXT,
    assigned_by VARCHAR(50),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (username, permission_code),
    CONSTRAINT fk_account_permission_override_account
        FOREIGN KEY (username) REFERENCES account(username) ON DELETE CASCADE,
    CONSTRAINT fk_account_permission_override_permission
        FOREIGN KEY (permission_code) REFERENCES permission(permission_code) ON DELETE CASCADE,
    CONSTRAINT fk_account_permission_override_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES account(username) ON DELETE SET NULL,
    CONSTRAINT chk_account_permission_override_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT chk_account_permission_override_expires_at CHECK (expires_at IS NULL OR expires_at > assigned_at)
);

-- -----------------------------------------------------------------------------
-- 8. Matrix/effective-permission read models
-- -----------------------------------------------------------------------------
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
    (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP) AS is_active
FROM role r
JOIN role_permission rp ON rp.role_code = r.role_code
JOIN permission p ON p.permission_code = rp.permission_code
JOIN permission_module pm ON pm.module_code = p.module_code
JOIN permission_resource pr ON pr.resource_code = p.resource_code
JOIN permission_action pa ON pa.action_code = p.action_code
JOIN permission_scope ps ON ps.scope_code = p.scope_code;

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
JOIN role_permission rp ON rp.role_code = ar.role_code
JOIN permission p ON p.permission_code = rp.permission_code
WHERE (ar.expires_at IS NULL OR ar.expires_at > CURRENT_TIMESTAMP)
  AND (rp.expires_at IS NULL OR rp.expires_at > CURRENT_TIMESTAMP)
  AND p.status = TRUE

UNION ALL

SELECT
    apo.username,
    NULL AS role_code,
    p.permission_code,
    p.module_code,
    p.resource_code,
    p.action_code,
    p.scope_code,
    apo.effect,
    'ACCOUNT_OVERRIDE' AS source_type,
    NULL AS account_role_expires_at,
    apo.expires_at AS permission_expires_at
FROM account_permission_override apo
JOIN permission p ON p.permission_code = apo.permission_code
WHERE (apo.expires_at IS NULL OR apo.expires_at > CURRENT_TIMESTAMP)
  AND p.status = TRUE;

-- -----------------------------------------------------------------------------
-- 9. Indexes for permission checks and admin screens
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_permission_matrix
    ON permission(module_code, resource_code, action_code, scope_code);

CREATE INDEX IF NOT EXISTS idx_permission_resource_module
    ON permission_resource(module_code, display_order);

CREATE INDEX IF NOT EXISTS idx_role_permission_permission
    ON role_permission(permission_code);

CREATE INDEX IF NOT EXISTS idx_role_permission_active
    ON role_permission(role_code, permission_code, effect)
    WHERE expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_account_role_role
    ON account_role(role_code);

CREATE INDEX IF NOT EXISTS idx_account_permission_override_permission
    ON account_permission_override(permission_code);
