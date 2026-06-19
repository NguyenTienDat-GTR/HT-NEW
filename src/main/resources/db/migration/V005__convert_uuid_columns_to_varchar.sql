-- 1) Executive board: position_code FK -> position(position_code)

ALTER TABLE executive_board_assignment
DROP CONSTRAINT IF EXISTS fk_executive_board_position;

ALTER TABLE position
ALTER COLUMN position_code TYPE VARCHAR(255)
USING position_code::text;

ALTER TABLE executive_board_assignment
ALTER COLUMN position_code TYPE VARCHAR(255)
USING position_code::text;

ALTER TABLE executive_board_assignment
    ADD CONSTRAINT fk_executive_board_position
        FOREIGN KEY (position_code)
            REFERENCES position(position_code);

-- 2) Convert id columns

ALTER TABLE executive_board_assignment
ALTER COLUMN id TYPE VARCHAR(255)
USING id::text;

ALTER TABLE course_participation
ALTER COLUMN id TYPE VARCHAR(255)
USING id::text;

ALTER TABLE account_role
ALTER COLUMN id TYPE VARCHAR(255)
USING id::text;

ALTER TABLE role_permission
ALTER COLUMN id TYPE VARCHAR(255)
USING id::text;

ALTER TABLE account_permission_override
ALTER COLUMN id TYPE VARCHAR(255)
USING id::text;

-- 3) Indexes

CREATE INDEX IF NOT EXISTS idx_position_code
    ON position(position_code);