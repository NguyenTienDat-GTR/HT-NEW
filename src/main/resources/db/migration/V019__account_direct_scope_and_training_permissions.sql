ALTER TABLE account
    ADD COLUMN IF NOT EXISTS diocese_id UUID,
    ADD COLUMN IF NOT EXISTS deanery_id UUID,
    ADD COLUMN IF NOT EXISTS parish_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_diocese_id'
    ) THEN
        ALTER TABLE account
            ADD CONSTRAINT fk_account_diocese_id
                FOREIGN KEY (diocese_id) REFERENCES diocese(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_deanery_id'
    ) THEN
        ALTER TABLE account
            ADD CONSTRAINT fk_account_deanery_id
                FOREIGN KEY (deanery_id) REFERENCES deanery(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_parish_id'
    ) THEN
        ALTER TABLE account
            ADD CONSTRAINT fk_account_parish_id
                FOREIGN KEY (parish_id) REFERENCES parish(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_account_diocese_id ON account(diocese_id);
CREATE INDEX IF NOT EXISTS idx_account_deanery_id ON account(deanery_id);
CREATE INDEX IF NOT EXISTS idx_account_parish_id ON account(parish_id);

INSERT INTO role (
    role_code,
    role_name,
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
    ('ADMIN_DIOCESE', 'Admin Diocese', 'Scoped diocesan administrator', TRUE, 100, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN_DEANERY', 'Admin Deanery', 'Scoped deanery administrator', TRUE, 200, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN_PARISH', 'Admin Parish', 'Scoped parish administrator', TRUE, 300, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (role_code) DO UPDATE
SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

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
    ('approve', 'Approve', 'Approve workflow request', 50, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('assign', 'Assign', 'Assign role to account', 60, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('submit', 'Submit', 'Submit workflow request', 70, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('score', 'Score', 'Update course score', 80, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE
SET
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

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
    'training',
    'Training',
    'Training lifecycle and certification',
    30,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (module_code) DO UPDATE
SET
    module_name = EXCLUDED.module_name,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    status = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM';

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
VALUES
    ('course', 'training', 'Training course', 'Training course management', 10, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('requirement', 'training', 'Course requirement', 'Course requirement management', 20, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('participation', 'training', 'Course participation', 'Training registration and approval workflow', 30, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('certificate', 'training', 'Certificate', 'Training certificate management', 40, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('position', 'organization', 'Position', 'Position management by unit type', 50, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('account_role', 'system', 'Account role', 'Scoped account role assignment', 60, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
    ('training.course.create.diocese', 'Create non-DT courses in own diocese', 'training', 'course', 'create', 'diocese', 'Create diocesan training courses except DT types', TRUE, 10, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.read.diocese', 'Read non-DT courses in own diocese', 'training', 'course', 'read', 'diocese', 'Read diocesan training courses except DT types', TRUE, 20, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.update.diocese', 'Update non-DT courses in own diocese', 'training', 'course', 'update', 'diocese', 'Update diocesan training courses except DT types', TRUE, 30, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.delete.diocese', 'Delete non-DT courses in own diocese', 'training', 'course', 'delete', 'diocese', 'Delete diocesan training courses except DT types', TRUE, 40, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.create.deanery', 'Create DT courses in own deanery', 'training', 'course', 'create', 'deanery', 'Create deanery DT training courses', TRUE, 50, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.read.deanery', 'Read DT courses in own deanery', 'training', 'course', 'read', 'deanery', 'Read deanery DT training courses', TRUE, 60, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.update.deanery', 'Update DT courses in own deanery', 'training', 'course', 'update', 'deanery', 'Update deanery DT training courses', TRUE, 70, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.course.delete.deanery', 'Delete DT courses in own deanery', 'training', 'course', 'delete', 'deanery', 'Delete deanery DT training courses', TRUE, 80, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.create.diocese', 'Create non-DT requirements in own diocese', 'training', 'requirement', 'create', 'diocese', 'Create diocesan training requirements', TRUE, 90, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.read.diocese', 'Read non-DT requirements in own diocese', 'training', 'requirement', 'read', 'diocese', 'Read diocesan training requirements', TRUE, 100, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.update.diocese', 'Update non-DT requirements in own diocese', 'training', 'requirement', 'update', 'diocese', 'Update diocesan training requirements', TRUE, 110, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.delete.diocese', 'Delete non-DT requirements in own diocese', 'training', 'requirement', 'delete', 'diocese', 'Delete diocesan training requirements', TRUE, 120, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.create.deanery', 'Create DT requirements in own deanery', 'training', 'requirement', 'create', 'deanery', 'Create deanery DT training requirements', TRUE, 130, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.read.deanery', 'Read DT requirements in own deanery', 'training', 'requirement', 'read', 'deanery', 'Read deanery DT training requirements', TRUE, 140, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.update.deanery', 'Update DT requirements in own deanery', 'training', 'requirement', 'update', 'deanery', 'Update deanery DT training requirements', TRUE, 150, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.requirement.delete.deanery', 'Delete DT requirements in own deanery', 'training', 'requirement', 'delete', 'deanery', 'Delete deanery DT training requirements', TRUE, 160, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.submit.parish', 'Submit training registrations in own parish', 'training', 'participation', 'submit', 'parish', 'Submit training registration lists from parish', TRUE, 170, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.read.deanery', 'Read training registrations in own deanery', 'training', 'participation', 'read', 'deanery', 'Read training registration workflow in own deanery', TRUE, 180, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.read.diocese', 'Read training registrations in own diocese', 'training', 'participation', 'read', 'diocese', 'Read training registration workflow in own diocese', TRUE, 190, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.submit.deanery', 'Submit non-DT registrations to diocese', 'training', 'participation', 'submit', 'deanery', 'Aggregate and submit non-DT registration workflow to diocese', TRUE, 195, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.approve.deanery', 'Approve DT exceptions in own deanery', 'training', 'participation', 'approve', 'deanery', 'Approve DT exception workflow in own deanery', TRUE, 200, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.approve.diocese', 'Approve non-DT exceptions in own diocese', 'training', 'participation', 'approve', 'diocese', 'Approve non-DT exception workflow in own diocese', TRUE, 210, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.score.deanery', 'Score DT courses in own deanery', 'training', 'participation', 'score', 'deanery', 'Update scores for DT courses in own deanery', TRUE, 220, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.participation.score.diocese', 'Score non-DT courses in own diocese', 'training', 'participation', 'score', 'diocese', 'Update scores for non-DT courses in own diocese', TRUE, 230, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.create.deanery', 'Create DT certificates in own deanery', 'training', 'certificate', 'create', 'deanery', 'Create DT certificates in own deanery', TRUE, 240, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.read.deanery', 'Read DT certificates in own deanery', 'training', 'certificate', 'read', 'deanery', 'Read DT certificates in own deanery', TRUE, 250, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.update.deanery', 'Update DT certificates in own deanery', 'training', 'certificate', 'update', 'deanery', 'Update DT certificates in own deanery', TRUE, 260, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.delete.deanery', 'Delete DT certificates in own deanery', 'training', 'certificate', 'delete', 'deanery', 'Delete DT certificates in own deanery', TRUE, 270, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.approve.deanery', 'Approve DT certificates in own deanery', 'training', 'certificate', 'approve', 'deanery', 'Approve DT certificates in own deanery', TRUE, 280, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.create.diocese', 'Create non-DT certificates in own diocese', 'training', 'certificate', 'create', 'diocese', 'Create non-DT certificates in own diocese', TRUE, 290, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.read.diocese', 'Read non-DT certificates in own diocese', 'training', 'certificate', 'read', 'diocese', 'Read non-DT certificates in own diocese', TRUE, 300, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.update.diocese', 'Update non-DT certificates in own diocese', 'training', 'certificate', 'update', 'diocese', 'Update non-DT certificates in own diocese', TRUE, 310, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.delete.diocese', 'Delete non-DT certificates in own diocese', 'training', 'certificate', 'delete', 'diocese', 'Delete non-DT certificates in own diocese', TRUE, 320, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.certificate.approve.diocese', 'Approve non-DT certificates in own diocese', 'training', 'certificate', 'approve', 'diocese', 'Approve non-DT certificates in own diocese', TRUE, 330, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.create.diocese', 'Create diocese positions', 'organization', 'position', 'create', 'diocese', 'Create diocese positions in own diocese', TRUE, 340, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.read.diocese', 'Read diocese positions', 'organization', 'position', 'read', 'diocese', 'Read diocese positions in own diocese', TRUE, 350, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.update.diocese', 'Update diocese positions', 'organization', 'position', 'update', 'diocese', 'Update diocese positions in own diocese', TRUE, 360, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.delete.diocese', 'Delete diocese positions', 'organization', 'position', 'delete', 'diocese', 'Delete diocese positions in own diocese', TRUE, 370, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.create.deanery', 'Create deanery positions', 'organization', 'position', 'create', 'deanery', 'Create deanery positions in own deanery', TRUE, 380, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.read.deanery', 'Read deanery positions', 'organization', 'position', 'read', 'deanery', 'Read deanery positions in own deanery', TRUE, 390, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.update.deanery', 'Update deanery positions', 'organization', 'position', 'update', 'deanery', 'Update deanery positions in own deanery', TRUE, 400, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.delete.deanery', 'Delete deanery positions', 'organization', 'position', 'delete', 'deanery', 'Delete deanery positions in own deanery', TRUE, 410, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.create.parish', 'Create parish positions', 'organization', 'position', 'create', 'parish', 'Create parish positions in own parish', TRUE, 420, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.read.parish', 'Read parish positions', 'organization', 'position', 'read', 'parish', 'Read parish positions in own parish', TRUE, 430, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.update.parish', 'Update parish positions', 'organization', 'position', 'update', 'parish', 'Update parish positions in own parish', TRUE, 440, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('organization.position.delete.parish', 'Delete parish positions', 'organization', 'position', 'delete', 'parish', 'Delete parish positions in own parish', TRUE, 450, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_role.assign.all', 'Assign any role', 'system', 'account_role', 'assign', 'all', 'Assign any role in the system', TRUE, 460, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_role.assign.diocese', 'Assign scoped roles in own diocese', 'system', 'account_role', 'assign', 'diocese', 'Assign equal or lower scoped roles in own diocese', TRUE, 470, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.account_role.assign.deanery', 'Assign scoped roles in own deanery', 'system', 'account_role', 'assign', 'deanery', 'Assign equal or lower scoped roles in own deanery', TRUE, 480, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
        'training.course.create.diocese', 'training.course.read.diocese', 'training.course.update.diocese', 'training.course.delete.diocese',
        'training.requirement.create.diocese', 'training.requirement.read.diocese', 'training.requirement.update.diocese', 'training.requirement.delete.diocese',
        'training.participation.read.diocese', 'training.participation.approve.diocese', 'training.participation.score.diocese',
        'training.certificate.create.diocese', 'training.certificate.read.diocese', 'training.certificate.update.diocese', 'training.certificate.delete.diocese', 'training.certificate.approve.diocese',
        'organization.position.create.diocese', 'organization.position.read.diocese', 'organization.position.update.diocese', 'organization.position.delete.diocese',
        'system.account_role.assign.diocese'
    ))
    OR (r.role_code = 'ADMIN_DEANERY' AND p.permission_code IN (
        'training.course.create.deanery', 'training.course.read.deanery', 'training.course.update.deanery', 'training.course.delete.deanery',
        'training.requirement.create.deanery', 'training.requirement.read.deanery', 'training.requirement.update.deanery', 'training.requirement.delete.deanery',
        'training.participation.read.deanery', 'training.participation.submit.deanery', 'training.participation.approve.deanery', 'training.participation.score.deanery',
        'training.certificate.create.deanery', 'training.certificate.read.deanery', 'training.certificate.update.deanery', 'training.certificate.delete.deanery', 'training.certificate.approve.deanery',
        'organization.position.create.deanery', 'organization.position.read.deanery', 'organization.position.update.deanery', 'organization.position.delete.deanery',
        'system.account_role.assign.deanery'
    ))
    OR (r.role_code = 'ADMIN_PARISH' AND p.permission_code IN (
        'training.participation.submit.parish',
        'organization.position.create.parish', 'organization.position.read.parish', 'organization.position.update.parish', 'organization.position.delete.parish'
    ))
)
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_DIOCESE', 'ADMIN_DEANERY', 'ADMIN_PARISH')
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = r.role_code
      AND rp.permission_code = p.permission_code
);

ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS diocese_id UUID,
    ADD COLUMN IF NOT EXISTS deanery_id UUID;

ALTER TABLE course_requirement
    ADD COLUMN IF NOT EXISTS diocese_id UUID,
    ADD COLUMN IF NOT EXISTS deanery_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_training_course_diocese_id'
    ) THEN
        ALTER TABLE training_course
            ADD CONSTRAINT fk_training_course_diocese_id
                FOREIGN KEY (diocese_id) REFERENCES diocese(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_training_course_deanery_id'
    ) THEN
        ALTER TABLE training_course
            ADD CONSTRAINT fk_training_course_deanery_id
                FOREIGN KEY (deanery_id) REFERENCES deanery(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_course_requirement_diocese_id'
    ) THEN
        ALTER TABLE course_requirement
            ADD CONSTRAINT fk_course_requirement_diocese_id
                FOREIGN KEY (diocese_id) REFERENCES diocese(id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_course_requirement_deanery_id'
    ) THEN
        ALTER TABLE course_requirement
            ADD CONSTRAINT fk_course_requirement_deanery_id
                FOREIGN KEY (deanery_id) REFERENCES deanery(id) ON DELETE SET NULL;
    END IF;
END $$;

ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS eligible BOOLEAN,
    ADD COLUMN IF NOT EXISTS exception_reason TEXT,
    ADD COLUMN IF NOT EXISTS parish_submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deanery_submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deanery_approval_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS diocese_approval_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS certificate_approval_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS certificate_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS certificate_approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255);

ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255);
