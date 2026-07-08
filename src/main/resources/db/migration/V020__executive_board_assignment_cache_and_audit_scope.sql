ALTER TABLE executive_board_assignment
    ALTER COLUMN id TYPE UUID
    USING NULLIF(id, '')::uuid;

ALTER TABLE executive_board_assignment
    ALTER COLUMN unit_id TYPE UUID
    USING NULLIF(unit_id, '')::uuid;

ALTER TABLE executive_board_assignment
    ADD COLUMN IF NOT EXISTS diocese_id UUID,
    ADD COLUMN IF NOT EXISTS deanery_id UUID,
    ADD COLUMN IF NOT EXISTS parish_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_executive_board_diocese_id'
    ) THEN
        ALTER TABLE executive_board_assignment
            ADD CONSTRAINT fk_executive_board_diocese_id
                FOREIGN KEY (diocese_id) REFERENCES diocese(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_executive_board_deanery_id'
    ) THEN
        ALTER TABLE executive_board_assignment
            ADD CONSTRAINT fk_executive_board_deanery_id
                FOREIGN KEY (deanery_id) REFERENCES deanery(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_executive_board_parish_id'
    ) THEN
        ALTER TABLE executive_board_assignment
            ADD CONSTRAINT fk_executive_board_parish_id
                FOREIGN KEY (parish_id) REFERENCES parish(id) ON DELETE SET NULL;
    END IF;
END $$;

UPDATE executive_board_assignment
SET
    diocese_id = CASE
        WHEN unit_type = 'DIOCESE' THEN unit_id
        WHEN unit_type = 'DEANERY' THEN (
            SELECT de.diocese_id
            FROM deanery de
            WHERE de.id = executive_board_assignment.unit_id
        )
        WHEN unit_type = 'PARISH' THEN (
            SELECT de.diocese_id
            FROM parish p
            JOIN deanery de ON de.id = p.deanery_id
            WHERE p.id = executive_board_assignment.unit_id
        )
        ELSE NULL
    END,
    deanery_id = CASE
        WHEN unit_type = 'DEANERY' THEN unit_id
        WHEN unit_type = 'PARISH' THEN (
            SELECT p.deanery_id
            FROM parish p
            WHERE p.id = executive_board_assignment.unit_id
        )
        ELSE NULL
    END,
    parish_id = CASE
        WHEN unit_type = 'PARISH' THEN unit_id
        ELSE NULL
    END
WHERE diocese_id IS NULL
   OR deanery_id IS NULL
   OR parish_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_executive_board_unit_uuid ON executive_board_assignment(unit_type, unit_id);
CREATE INDEX IF NOT EXISTS idx_executive_board_leader_id ON executive_board_assignment(leader_id);
CREATE INDEX IF NOT EXISTS idx_executive_board_position_id ON executive_board_assignment(position_id);
CREATE INDEX IF NOT EXISTS idx_executive_board_diocese_id ON executive_board_assignment(diocese_id);
CREATE INDEX IF NOT EXISTS idx_executive_board_deanery_id ON executive_board_assignment(deanery_id);
CREATE INDEX IF NOT EXISTS idx_executive_board_parish_id ON executive_board_assignment(parish_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_executive_board_active_position_per_unit
    ON executive_board_assignment(unit_type, unit_id, position_id)
    WHERE status = TRUE AND end_date IS NULL;

ALTER TABLE system_log
    ADD COLUMN IF NOT EXISTS unit_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS unit_id UUID,
    ADD COLUMN IF NOT EXISTS diocese_id UUID,
    ADD COLUMN IF NOT EXISTS deanery_id UUID,
    ADD COLUMN IF NOT EXISTS parish_id UUID;

CREATE INDEX IF NOT EXISTS idx_system_log_diocese_id ON system_log(diocese_id);
CREATE INDEX IF NOT EXISTS idx_system_log_deanery_id ON system_log(deanery_id);
CREATE INDEX IF NOT EXISTS idx_system_log_parish_id ON system_log(parish_id);
CREATE INDEX IF NOT EXISTS idx_system_log_unit_scope ON system_log(unit_type, unit_id);

UPDATE system_log
SET
    unit_type = 'DIOCESE',
    unit_id = resource_id::uuid,
    diocese_id = resource_id::uuid
WHERE resource = 'diocese'
  AND resource_id ~* '^[0-9a-f-]{36}$';

UPDATE system_log sl
SET
    unit_type = 'DEANERY',
    unit_id = sl.resource_id::uuid,
    deanery_id = de.id,
    diocese_id = de.diocese_id
FROM deanery de
WHERE sl.resource = 'deanery'
  AND sl.resource_id ~* '^[0-9a-f-]{36}$'
  AND de.id = sl.resource_id::uuid;

UPDATE system_log sl
SET
    unit_type = 'PARISH',
    unit_id = sl.resource_id::uuid,
    parish_id = p.id,
    deanery_id = p.deanery_id,
    diocese_id = de.diocese_id
FROM parish p
JOIN deanery de ON de.id = p.deanery_id
WHERE sl.resource = 'parish'
  AND sl.resource_id ~* '^[0-9a-f-]{36}$'
  AND p.id = sl.resource_id::uuid;

UPDATE system_log sl
SET
    unit_type = 'PARISH',
    unit_id = p.id,
    parish_id = p.id,
    deanery_id = p.deanery_id,
    diocese_id = de.diocese_id
FROM leader l
JOIN parish p ON p.id = l.parish_id
JOIN deanery de ON de.id = p.deanery_id
WHERE sl.resource = 'leader'
  AND sl.resource_id ~* '^[0-9a-f-]{36}$'
  AND l.id = sl.resource_id::uuid;

UPDATE system_log sl
SET
    unit_type = CASE
        WHEN tc.deanery_id IS NOT NULL THEN 'DEANERY'
        WHEN tc.diocese_id IS NOT NULL THEN 'DIOCESE'
        ELSE sl.unit_type
    END,
    unit_id = COALESCE(tc.deanery_id, tc.diocese_id, sl.unit_id),
    deanery_id = tc.deanery_id,
    diocese_id = COALESCE(tc.diocese_id, de.diocese_id, sl.diocese_id)
FROM training_course tc
LEFT JOIN deanery de ON de.id = tc.deanery_id
WHERE sl.resource = 'course'
  AND tc.course_code = sl.resource_id;

UPDATE system_log sl
SET
    unit_type = CASE
        WHEN cr.deanery_id IS NOT NULL THEN 'DEANERY'
        WHEN cr.diocese_id IS NOT NULL THEN 'DIOCESE'
        ELSE sl.unit_type
    END,
    unit_id = COALESCE(cr.deanery_id, cr.diocese_id, sl.unit_id),
    deanery_id = cr.deanery_id,
    diocese_id = COALESCE(cr.diocese_id, de.diocese_id, sl.diocese_id)
FROM course_requirement cr
LEFT JOIN deanery de ON de.id = cr.deanery_id
WHERE sl.resource = 'requirement'
  AND cr.requirement_code = sl.resource_id;

UPDATE system_log sl
SET
    unit_type = CASE
        WHEN c.course_code IS NOT NULL AND tc.deanery_id IS NOT NULL THEN 'DEANERY'
        WHEN c.course_code IS NOT NULL AND tc.diocese_id IS NOT NULL THEN 'DIOCESE'
        ELSE sl.unit_type
    END,
    unit_id = COALESCE(tc.deanery_id, tc.diocese_id, sl.unit_id),
    deanery_id = COALESCE(tc.deanery_id, sl.deanery_id),
    diocese_id = COALESCE(tc.diocese_id, de.diocese_id, sl.diocese_id)
FROM certificate c
LEFT JOIN training_course tc ON tc.course_code = c.course_code
LEFT JOIN deanery de ON de.id = tc.deanery_id
WHERE sl.resource = 'certificate'
  AND c.certificate_code = sl.resource_id;

UPDATE system_log sl
SET
    unit_type = e.unit_type,
    unit_id = e.unit_id,
    diocese_id = e.diocese_id,
    deanery_id = e.deanery_id,
    parish_id = e.parish_id
FROM executive_board_assignment e
WHERE sl.resource = 'executive_board_assignment'
  AND sl.resource_id ~* '^[0-9a-f-]{36}$'
  AND e.id = sl.resource_id::uuid;

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
VALUES (
    'executive_board_assignment',
    'organization',
    'Executive board assignment',
    'Assign leaders to positions by organization unit',
    55,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (resource_code) DO UPDATE
SET
    module_code = EXCLUDED.module_code,
    resource_name = EXCLUDED.resource_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code,
    permission_name,
    module_code,
    resource_code,
    action_code,
    scope_code,
    description,
    is_system,
    display_order,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
VALUES
    ('organization.executive_board_assignment.create.diocese', 'Create diocese executive board assignments', 'organization', 'executive_board_assignment', 'create', 'diocese', 'Create executive board assignments at diocese scope', TRUE, 490, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.read.diocese', 'Read diocese executive board assignments', 'organization', 'executive_board_assignment', 'read', 'diocese', 'Read executive board assignments at diocese scope', TRUE, 500, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.update.diocese', 'Update diocese executive board assignments', 'organization', 'executive_board_assignment', 'update', 'diocese', 'Update executive board assignments at diocese scope', TRUE, 510, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.create.deanery', 'Create deanery executive board assignments', 'organization', 'executive_board_assignment', 'create', 'deanery', 'Create executive board assignments at deanery scope', TRUE, 520, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.read.deanery', 'Read deanery executive board assignments', 'organization', 'executive_board_assignment', 'read', 'deanery', 'Read executive board assignments at deanery scope', TRUE, 530, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.update.deanery', 'Update deanery executive board assignments', 'organization', 'executive_board_assignment', 'update', 'deanery', 'Update executive board assignments at deanery scope', TRUE, 540, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.create.parish', 'Create parish executive board assignments', 'organization', 'executive_board_assignment', 'create', 'parish', 'Create executive board assignments at parish scope', TRUE, 550, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.read.parish', 'Read parish executive board assignments', 'organization', 'executive_board_assignment', 'read', 'parish', 'Read executive board assignments at parish scope', TRUE, 560, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.executive_board_assignment.update.parish', 'Update parish executive board assignments', 'organization', 'executive_board_assignment', 'update', 'parish', 'Update executive board assignments at parish scope', TRUE, 570, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.audit_log.read.diocese', 'Read audit logs in own diocese', 'system', 'audit_log', 'read', 'diocese', 'Read audit logs in own diocese and descendants', TRUE, 580, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.audit_log.read.deanery', 'Read audit logs in own deanery', 'system', 'audit_log', 'read', 'deanery', 'Read audit logs in own deanery and descendants', TRUE, 590, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.audit_log.read.parish', 'Read audit logs in own parish', 'system', 'audit_log', 'read', 'parish', 'Read audit logs in own parish', TRUE, 600, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET
    permission_name = EXCLUDED.permission_name,
    module_code = EXCLUDED.module_code,
    resource_code = EXCLUDED.resource_code,
    action_code = EXCLUDED.action_code,
    scope_code = EXCLUDED.scope_code,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

INSERT INTO role_permission (
    id,
    role_code,
    permission_code,
    effect,
    assigned_by,
    assigned_at,
    status,
    created_at,
    created_by,
    updated_at,
    updated_by
)
SELECT
    (
        substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 21, 12)
    )::uuid,
    r.role_code,
    p.permission_code,
    'ALLOW',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
FROM role r
JOIN permission p ON (
    r.role_code = 'SUPER_ADMIN'
    OR (r.role_code = 'ADMIN_DIOCESE' AND p.permission_code IN (
        'organization.executive_board_assignment.create.diocese',
        'organization.executive_board_assignment.read.diocese',
        'organization.executive_board_assignment.update.diocese',
        'organization.executive_board_assignment.create.deanery',
        'organization.executive_board_assignment.read.deanery',
        'organization.executive_board_assignment.update.deanery',
        'organization.executive_board_assignment.create.parish',
        'organization.executive_board_assignment.read.parish',
        'organization.executive_board_assignment.update.parish',
        'system.audit_log.read.diocese'
    ))
    OR (r.role_code = 'ADMIN_DEANERY' AND p.permission_code IN (
        'organization.executive_board_assignment.create.deanery',
        'organization.executive_board_assignment.read.deanery',
        'organization.executive_board_assignment.update.deanery',
        'organization.executive_board_assignment.create.parish',
        'organization.executive_board_assignment.read.parish',
        'organization.executive_board_assignment.update.parish',
        'system.audit_log.read.deanery'
    ))
    OR (r.role_code = 'ADMIN_PARISH' AND p.permission_code IN (
        'organization.executive_board_assignment.create.parish',
        'organization.executive_board_assignment.read.parish',
        'organization.executive_board_assignment.update.parish',
        'system.audit_log.read.parish'
    ))
)
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_DIOCESE', 'ADMIN_DEANERY', 'ADMIN_PARISH')
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = r.role_code
      AND rp.permission_code = p.permission_code
);
