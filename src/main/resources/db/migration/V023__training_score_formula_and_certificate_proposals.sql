CREATE TABLE IF NOT EXISTS score_component_template (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    max_score NUMERIC(10, 2) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    unit_type VARCHAR(50) NOT NULL,
    diocese_id UUID,
    deanery_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    status BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_score_component_template_scope
        CHECK (
            (unit_type = 'DIOCESE' AND diocese_id IS NOT NULL AND deanery_id IS NULL)
            OR (unit_type = 'DEANERY' AND deanery_id IS NOT NULL AND diocese_id IS NULL)
        )
);

CREATE TABLE IF NOT EXISTS score_formula_template (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    expression TEXT NOT NULL,
    passing_score NUMERIC(10, 2) NOT NULL,
    description TEXT,
    unit_type VARCHAR(50) NOT NULL,
    diocese_id UUID,
    deanery_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    status BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_score_formula_template_scope
        CHECK (
            (unit_type = 'DIOCESE' AND diocese_id IS NOT NULL AND deanery_id IS NULL)
            OR (unit_type = 'DEANERY' AND deanery_id IS NOT NULL AND diocese_id IS NULL)
        )
);

CREATE TABLE IF NOT EXISTS score_formula_component (
    id VARCHAR(36) PRIMARY KEY,
    formula_id VARCHAR(36) NOT NULL,
    component_id VARCHAR(36) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    status BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS course_participation_component_score (
    id VARCHAR(36) PRIMARY KEY,
    participation_id VARCHAR(36) NOT NULL,
    component_id VARCHAR(36) NOT NULL,
    score NUMERIC(10, 2) NOT NULL,
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by VARCHAR(100),
    status BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS score_formula_id VARCHAR(36);

ALTER TABLE certificate
    ADD COLUMN IF NOT EXISTS system_suggested BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approved_by_exception BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS exception_approval_reason TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_score_component_template_diocese_id'
    ) THEN
        ALTER TABLE score_component_template
            ADD CONSTRAINT fk_score_component_template_diocese_id
                FOREIGN KEY (diocese_id) REFERENCES diocese(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_score_component_template_deanery_id'
    ) THEN
        ALTER TABLE score_component_template
            ADD CONSTRAINT fk_score_component_template_deanery_id
                FOREIGN KEY (deanery_id) REFERENCES deanery(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_score_formula_template_diocese_id'
    ) THEN
        ALTER TABLE score_formula_template
            ADD CONSTRAINT fk_score_formula_template_diocese_id
                FOREIGN KEY (diocese_id) REFERENCES diocese(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_score_formula_template_deanery_id'
    ) THEN
        ALTER TABLE score_formula_template
            ADD CONSTRAINT fk_score_formula_template_deanery_id
                FOREIGN KEY (deanery_id) REFERENCES deanery(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_score_formula_component_formula_id'
    ) THEN
        ALTER TABLE score_formula_component
            ADD CONSTRAINT fk_score_formula_component_formula_id
                FOREIGN KEY (formula_id) REFERENCES score_formula_template(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_score_formula_component_component_id'
    ) THEN
        ALTER TABLE score_formula_component
            ADD CONSTRAINT fk_score_formula_component_component_id
                FOREIGN KEY (component_id) REFERENCES score_component_template(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_course_participation_component_score_participation_id'
    ) THEN
        ALTER TABLE course_participation_component_score
            ADD CONSTRAINT fk_course_participation_component_score_participation_id
                FOREIGN KEY (participation_id) REFERENCES course_participation(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_course_participation_component_score_component_id'
    ) THEN
        ALTER TABLE course_participation_component_score
            ADD CONSTRAINT fk_course_participation_component_score_component_id
                FOREIGN KEY (component_id) REFERENCES score_component_template(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_training_course_score_formula_id'
    ) THEN
        ALTER TABLE training_course
            ADD CONSTRAINT fk_training_course_score_formula_id
                FOREIGN KEY (score_formula_id) REFERENCES score_formula_template(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_score_component_template_diocese_id ON score_component_template(diocese_id);
CREATE INDEX IF NOT EXISTS idx_score_component_template_deanery_id ON score_component_template(deanery_id);
CREATE INDEX IF NOT EXISTS idx_score_formula_template_diocese_id ON score_formula_template(diocese_id);
CREATE INDEX IF NOT EXISTS idx_score_formula_template_deanery_id ON score_formula_template(deanery_id);
CREATE INDEX IF NOT EXISTS idx_score_formula_component_formula_id ON score_formula_component(formula_id);
CREATE INDEX IF NOT EXISTS idx_score_formula_component_component_id ON score_formula_component(component_id);
CREATE INDEX IF NOT EXISTS idx_course_participation_component_score_participation_id ON course_participation_component_score(participation_id);
CREATE INDEX IF NOT EXISTS idx_course_participation_component_score_component_id ON course_participation_component_score(component_id);
CREATE INDEX IF NOT EXISTS idx_training_course_score_formula_id ON training_course(score_formula_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_score_component_template_diocese_code
    ON score_component_template(diocese_id, code)
    WHERE unit_type = 'DIOCESE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_score_component_template_deanery_code
    ON score_component_template(deanery_id, code)
    WHERE unit_type = 'DEANERY';

CREATE UNIQUE INDEX IF NOT EXISTS uk_score_formula_template_diocese_code
    ON score_formula_template(diocese_id, code)
    WHERE unit_type = 'DIOCESE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_score_formula_template_deanery_code
    ON score_formula_template(deanery_id, code)
    WHERE unit_type = 'DEANERY';

CREATE UNIQUE INDEX IF NOT EXISTS uk_score_formula_component_formula_component
    ON score_formula_component(formula_id, component_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_course_participation_component_score_participation_component
    ON course_participation_component_score(participation_id, component_id);

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
    ('score_component', 'training', 'Score component', 'Training score component template management', 45, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('score_formula', 'training', 'Score formula', 'Training score formula template management', 46, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
    ('training.score_component.create.diocese', 'Create diocese score components', 'training', 'score_component', 'create', 'diocese', 'Create score component templates in own diocese', TRUE, 500, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.read.diocese', 'Read diocese score components', 'training', 'score_component', 'read', 'diocese', 'Read score component templates in own diocese', TRUE, 510, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.update.diocese', 'Update diocese score components', 'training', 'score_component', 'update', 'diocese', 'Update score component templates in own diocese', TRUE, 520, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.delete.diocese', 'Delete diocese score components', 'training', 'score_component', 'delete', 'diocese', 'Delete score component templates in own diocese', TRUE, 530, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.create.deanery', 'Create deanery score components', 'training', 'score_component', 'create', 'deanery', 'Create score component templates in own deanery', TRUE, 540, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.read.deanery', 'Read deanery score components', 'training', 'score_component', 'read', 'deanery', 'Read score component templates in own deanery', TRUE, 550, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.update.deanery', 'Update deanery score components', 'training', 'score_component', 'update', 'deanery', 'Update score component templates in own deanery', TRUE, 560, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_component.delete.deanery', 'Delete deanery score components', 'training', 'score_component', 'delete', 'deanery', 'Delete score component templates in own deanery', TRUE, 570, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.create.diocese', 'Create diocese score formulas', 'training', 'score_formula', 'create', 'diocese', 'Create score formula templates in own diocese', TRUE, 580, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.read.diocese', 'Read diocese score formulas', 'training', 'score_formula', 'read', 'diocese', 'Read score formula templates in own diocese', TRUE, 590, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.update.diocese', 'Update diocese score formulas', 'training', 'score_formula', 'update', 'diocese', 'Update score formula templates in own diocese', TRUE, 600, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.delete.diocese', 'Delete diocese score formulas', 'training', 'score_formula', 'delete', 'diocese', 'Delete score formula templates in own diocese', TRUE, 610, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.create.deanery', 'Create deanery score formulas', 'training', 'score_formula', 'create', 'deanery', 'Create score formula templates in own deanery', TRUE, 620, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.read.deanery', 'Read deanery score formulas', 'training', 'score_formula', 'read', 'deanery', 'Read score formula templates in own deanery', TRUE, 630, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.update.deanery', 'Update deanery score formulas', 'training', 'score_formula', 'update', 'deanery', 'Update score formula templates in own deanery', TRUE, 640, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('training.score_formula.delete.deanery', 'Delete deanery score formulas', 'training', 'score_formula', 'delete', 'deanery', 'Delete score formula templates in own deanery', TRUE, 650, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
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
        'training.score_component.create.diocese', 'training.score_component.read.diocese', 'training.score_component.update.diocese', 'training.score_component.delete.diocese',
        'training.score_formula.create.diocese', 'training.score_formula.read.diocese', 'training.score_formula.update.diocese', 'training.score_formula.delete.diocese'
    ))
    OR (r.role_code = 'ADMIN_DEANERY' AND p.permission_code IN (
        'training.score_component.create.deanery', 'training.score_component.read.deanery', 'training.score_component.update.deanery', 'training.score_component.delete.deanery',
        'training.score_formula.create.deanery', 'training.score_formula.read.deanery', 'training.score_formula.update.deanery', 'training.score_formula.delete.deanery'
    ))
)
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN_DIOCESE', 'ADMIN_DEANERY')
AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_code = r.role_code
      AND rp.permission_code = p.permission_code
);
