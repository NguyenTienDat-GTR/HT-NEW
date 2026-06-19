-- ============================================================================
-- FLYWAY MIGRATION: V001
-- Description: Initial table creation based on JPA entities
-- Date: 2026-06-19
-- ============================================================================

-- ============================================================================
-- PERMISSION MANAGEMENT TABLES
-- ============================================================================

-- Create permission_module table
CREATE TABLE permission_module
(
    module_code   VARCHAR(255) PRIMARY KEY,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    module_name   VARCHAR(255),
    description   TEXT,
    display_order INTEGER,
    CONSTRAINT uk_permission_module_code UNIQUE (module_code)
);

-- Create permission_resource table
CREATE TABLE permission_resource
(
    resource_code VARCHAR(255) PRIMARY KEY,
    module_code   VARCHAR(255)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    resource_name VARCHAR(255),
    description   TEXT,
    display_order INTEGER,
    CONSTRAINT fk_permission_resource_module FOREIGN KEY (module_code) REFERENCES permission_module (module_code),
    CONSTRAINT uk_permission_resource_code UNIQUE (resource_code)
);

-- Create permission_action table
CREATE TABLE permission_action
(
    action_code   VARCHAR(255) PRIMARY KEY,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    action_name   VARCHAR(255),
    description   TEXT,
    display_order INTEGER,
    CONSTRAINT uk_permission_action_code UNIQUE (action_code)
);

-- Create permission_scope table
CREATE TABLE permission_scope
(
    scope_code    VARCHAR(255) PRIMARY KEY,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    scope_name    VARCHAR(255),
    description   TEXT,
    display_order INTEGER,
    CONSTRAINT uk_permission_scope_code UNIQUE (scope_code)
);

-- Create permission table
CREATE TABLE permission
(
    permission_code VARCHAR(255) PRIMARY KEY,
    module_code     VARCHAR(255),
    resource_code   VARCHAR(255),
    action_code     VARCHAR(255),
    scope_code      VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMP WITH TIME ZONE,
    updated_by      VARCHAR(100),
    status          BOOLEAN                  NOT NULL DEFAULT TRUE,
    permission_name VARCHAR(255),
    description     TEXT,
    is_system       BOOLEAN,
    display_order   INTEGER,
    CONSTRAINT fk_permission_module FOREIGN KEY (module_code) REFERENCES permission_module (module_code),
    CONSTRAINT fk_permission_resource FOREIGN KEY (resource_code) REFERENCES permission_resource (resource_code),
    CONSTRAINT fk_permission_action FOREIGN KEY (action_code) REFERENCES permission_action (action_code),
    CONSTRAINT fk_permission_scope FOREIGN KEY (scope_code) REFERENCES permission_scope (scope_code),
    CONSTRAINT uk_permission_code UNIQUE (permission_code)
);

-- ============================================================================
-- ORGANIZATION UNIT TABLES (Diocese, Deanery, Parish)
-- ============================================================================

-- Create diocese table
CREATE TABLE diocese
(
    diocese_code       VARCHAR(20) PRIMARY KEY,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMP WITH TIME ZONE,
    updated_by         VARCHAR(100),
    status             BOOLEAN                  NOT NULL DEFAULT TRUE,
    name               VARCHAR(100)             NOT NULL,
    patron_saint_day   DATE,
    establishment_date DATE,
    chaplain           VARCHAR(100),
    phone_number       VARCHAR(15),
    email              VARCHAR(100),
    union_name         VARCHAR(100)             NOT NULL,
    CONSTRAINT uk_diocese_code UNIQUE (diocese_code)
);

-- Create deanery table
CREATE TABLE deanery
(
    deanery_code       VARCHAR(20) PRIMARY KEY,
    diocese_code       VARCHAR(20)              NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMP WITH TIME ZONE,
    updated_by         VARCHAR(100),
    status             BOOLEAN                  NOT NULL DEFAULT TRUE,
    name               VARCHAR(100)             NOT NULL,
    patron_saint_day   DATE,
    establishment_date DATE,
    chaplain           VARCHAR(100),
    phone_number       VARCHAR(15),
    email              VARCHAR(100),
    association_name   VARCHAR(255)             NOT NULL,
    CONSTRAINT fk_deanery_diocese FOREIGN KEY (diocese_code) REFERENCES diocese (diocese_code),
    CONSTRAINT uk_deanery_code UNIQUE (deanery_code)
);

-- Create parish table
CREATE TABLE parish
(
    parish_code        VARCHAR(20) PRIMARY KEY,
    deanery_code       VARCHAR(20)              NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMP WITH TIME ZONE,
    updated_by         VARCHAR(100),
    status             BOOLEAN                  NOT NULL DEFAULT TRUE,
    name               VARCHAR(100)             NOT NULL,
    patron_saint_day   DATE,
    establishment_date DATE,
    chaplain           VARCHAR(100),
    phone_number       VARCHAR(15),
    email              VARCHAR(100),
    chapter_name       VARCHAR(255)             NOT NULL,
    CONSTRAINT fk_parish_deanery FOREIGN KEY (deanery_code) REFERENCES deanery (deanery_code),
    CONSTRAINT uk_parish_code UNIQUE (parish_code)
);

-- ============================================================================
-- LEADER AND RELATED TABLES
-- ============================================================================

-- Create leader table
CREATE TABLE leader
(
    leader_code  VARCHAR(20) PRIMARY KEY,
    parish_code  VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP WITH TIME ZONE,
    updated_by   VARCHAR(100),
    status       BOOLEAN                  NOT NULL DEFAULT TRUE,
    holy_name    VARCHAR(50)              NOT NULL,
    first_name   VARCHAR(100)             NOT NULL,
    last_name    VARCHAR(100)             NOT NULL,
    birth_date   DATE                     NOT NULL,
    email        VARCHAR(100)             NOT NULL UNIQUE,
    phone_number VARCHAR(15),
    leader_level VARCHAR(50)              NOT NULL,
    CONSTRAINT fk_leader_parish FOREIGN KEY (parish_code) REFERENCES parish (parish_code),
    CONSTRAINT uk_leader_code UNIQUE (leader_code),
    CONSTRAINT uk_leader_email UNIQUE (email)
);

-- Create position table
CREATE TABLE position
(
    position_code UUID PRIMARY KEY,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    position_name VARCHAR(255)             NOT NULL,
    position_type VARCHAR(50),
    CONSTRAINT uk_position_code UNIQUE (position_code)
);

-- Create rank_history table
CREATE TABLE rank_history
(
    id             BIGSERIAL PRIMARY KEY,
    leader_code    VARCHAR(20),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMP WITH TIME ZONE,
    updated_by     VARCHAR(100),
    status         BOOLEAN                  NOT NULL DEFAULT TRUE,
    old_level      VARCHAR(50),
    new_level      VARCHAR(50),
    promotion_date DATE,
    note           TEXT,
    CONSTRAINT fk_rank_history_leader FOREIGN KEY (leader_code) REFERENCES leader (leader_code)
);

-- Create executive_board_assignment table
CREATE TABLE executive_board_assignment
(
    id            UUID PRIMARY KEY,
    leader_code   VARCHAR(20),
    position_code UUID,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    unit_type     VARCHAR(50)              NOT NULL,
    unit_id       VARCHAR(20)              NOT NULL,
    start_date    DATE,
    end_date      DATE,
    CONSTRAINT fk_executive_board_leader FOREIGN KEY (leader_code) REFERENCES leader (leader_code),
    CONSTRAINT fk_executive_board_position FOREIGN KEY (position_code) REFERENCES position (position_code)
);

-- ============================================================================
-- TRAINING RELATED TABLES
-- ============================================================================

-- Create course_requirement table
CREATE TABLE course_requirement
(
    requirement_code       VARCHAR(255) PRIMARY KEY,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by             VARCHAR(100),
    updated_at             TIMESTAMP WITH TIME ZONE,
    updated_by             VARCHAR(100),
    status                 BOOLEAN                  NOT NULL DEFAULT TRUE,
    requirement_name       VARCHAR(255),
    course_type            VARCHAR(50),
    required_current_level VARCHAR(50),
    required_min_age       INTEGER,
    required_years_active  INTEGER,
    CONSTRAINT uk_course_requirement_code UNIQUE (requirement_code)
);

-- Create training_course table
CREATE TABLE training_course
(
    course_code      VARCHAR(255) PRIMARY KEY,
    requirement_code VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMP WITH TIME ZONE,
    updated_by       VARCHAR(100),
    status           BOOLEAN                  NOT NULL DEFAULT TRUE,
    course_name      VARCHAR(255),
    host_year        INTEGER,
    location         VARCHAR(255),
    start_date       DATE,
    end_date         DATE,
    course_type      VARCHAR(255),
    additional_notes TEXT,
    CONSTRAINT fk_training_course_requirement FOREIGN KEY (requirement_code) REFERENCES course_requirement (requirement_code),
    CONSTRAINT uk_training_course_code UNIQUE (course_code)
);

-- Create course_participation table
CREATE TABLE course_participation
(
    id                   UUID PRIMARY KEY,
    leader_code          VARCHAR(20),
    course_code          VARCHAR(255),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR(100),
    updated_at           TIMESTAMP WITH TIME ZONE,
    updated_by           VARCHAR(100),
    status               BOOLEAN                  NOT NULL DEFAULT TRUE,
    registration_date    DATE,
    participation_status VARCHAR(50),
    total_score          NUMERIC(10, 2),
    result               VARCHAR(50),
    review               TEXT,
    CONSTRAINT fk_course_participation_leader FOREIGN KEY (leader_code) REFERENCES leader (leader_code),
    CONSTRAINT fk_course_participation_course FOREIGN KEY (course_code) REFERENCES training_course (course_code)
);

-- ============================================================================
-- CERTIFICATE TABLE
-- ============================================================================

-- Create certificate table
CREATE TABLE certificate
(
    certificate_code     VARCHAR(255) PRIMARY KEY,
    leader_code          VARCHAR(20),
    course_code          VARCHAR(255),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR(100),
    updated_at           TIMESTAMP WITH TIME ZONE,
    updated_by           VARCHAR(100),
    status               BOOLEAN                  NOT NULL DEFAULT TRUE,
    old_certificate_code VARCHAR(255),
    certificate_name     VARCHAR(255),
    result               VARCHAR(255),
    signed_by            VARCHAR(255),
    issue_date           DATE,
    image_url            TEXT,
    CONSTRAINT fk_certificate_leader FOREIGN KEY (leader_code) REFERENCES leader (leader_code),
    CONSTRAINT fk_certificate_course FOREIGN KEY (course_code) REFERENCES training_course (course_code),
    CONSTRAINT uk_certificate_code UNIQUE (certificate_code)
);

-- ============================================================================
-- SYSTEM/ACCOUNT MANAGEMENT TABLES
-- ============================================================================

-- Create role table
CREATE TABLE role
(
    role_code     VARCHAR(255) PRIMARY KEY,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    role_name     VARCHAR(255),
    description   TEXT,
    is_system     BOOLEAN,
    display_order INTEGER,
    CONSTRAINT uk_role_code UNIQUE (role_code)
);

-- Create account table
CREATE TABLE account
(
    username      VARCHAR(255) PRIMARY KEY,
    leader_code   VARCHAR(20),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN                  NOT NULL DEFAULT TRUE,
    password      VARCHAR(255),
    auth_provider VARCHAR(255),
    provider_id   VARCHAR(255),
    avatar_url    TEXT,
    CONSTRAINT fk_account_leader FOREIGN KEY (leader_code) REFERENCES leader (leader_code)
);

-- Create account_role table
CREATE TABLE account_role
(
    id          UUID PRIMARY KEY,
    username    VARCHAR(255)             NOT NULL,
    role_code   VARCHAR(255)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(100),
    updated_at  TIMESTAMP WITH TIME ZONE,
    updated_by  VARCHAR(100),
    status      BOOLEAN                  NOT NULL DEFAULT TRUE,
    is_primary  BOOLEAN,
    assigned_by VARCHAR(255),
    assigned_at TIMESTAMP WITH TIME ZONE,
    expires_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_account_role_account FOREIGN KEY (username) REFERENCES account (username),
    CONSTRAINT fk_account_role_role FOREIGN KEY (role_code) REFERENCES role (role_code),
    CONSTRAINT uk_account_role UNIQUE (username, role_code)
);

-- Create role_permission table
CREATE TABLE role_permission
(
    id              UUID PRIMARY KEY,
    role_code       VARCHAR(255)             NOT NULL,
    permission_code VARCHAR(255)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMP WITH TIME ZONE,
    updated_by      VARCHAR(100),
    status          BOOLEAN                  NOT NULL DEFAULT TRUE,
    effect          VARCHAR(50)              NOT NULL,
    conditions      TEXT,
    assigned_by     VARCHAR(255),
    assigned_at     TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_code) REFERENCES role (role_code),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_code) REFERENCES permission (permission_code),
    CONSTRAINT uk_role_permission UNIQUE (role_code, permission_code)
);

-- Create account_permission_override table
CREATE TABLE account_permission_override
(
    id              UUID PRIMARY KEY,
    username        VARCHAR(255)             NOT NULL,
    permission_code VARCHAR(255)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMP WITH TIME ZONE,
    updated_by      VARCHAR(100),
    status          BOOLEAN                  NOT NULL DEFAULT TRUE,
    effect          VARCHAR(50)              NOT NULL,
    reason          TEXT,
    expires_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_account_permission_account FOREIGN KEY (username) REFERENCES account (username),
    CONSTRAINT fk_account_permission_permission FOREIGN KEY (permission_code) REFERENCES permission (permission_code),
    CONSTRAINT uk_account_permission_override UNIQUE (username, permission_code)
);

-- ============================================================================
-- CREATE INDEXES FOR PERFORMANCE
-- ============================================================================

-- Leader indexes
CREATE INDEX idx_leader_parish_code ON leader (parish_code);
CREATE INDEX idx_leader_email ON leader (email);
CREATE INDEX idx_leader_leader_level ON leader (leader_level);

-- Diocese/Deanery/Parish indexes
CREATE INDEX idx_deanery_diocese_code ON deanery (diocese_code);
CREATE INDEX idx_parish_deanery_code ON parish (deanery_code);

-- Training related indexes
CREATE INDEX idx_training_course_requirement_code ON training_course (requirement_code);
CREATE INDEX idx_course_participation_leader_code ON course_participation (leader_code);
CREATE INDEX idx_course_participation_course_code ON course_participation (course_code);
CREATE INDEX idx_course_participation_status ON course_participation (participation_status);

-- Certificate indexes
CREATE INDEX idx_certificate_leader_code ON certificate (leader_code);
CREATE INDEX idx_certificate_course_code ON certificate (course_code);
CREATE INDEX idx_certificate_issue_date ON certificate (issue_date);

-- Rank history indexes
CREATE INDEX idx_rank_history_leader_code ON rank_history (leader_code);
CREATE INDEX idx_rank_history_promotion_date ON rank_history (promotion_date);

-- Executive board assignment indexes
CREATE INDEX idx_executive_board_leader_code ON executive_board_assignment (leader_code);
CREATE INDEX idx_executive_board_position_code ON executive_board_assignment (position_code);
CREATE INDEX idx_executive_board_unit ON executive_board_assignment (unit_type, unit_id);

-- Account and role indexes
CREATE INDEX idx_account_role_username ON account_role (username);
CREATE INDEX idx_account_role_role_code ON account_role (role_code);
CREATE INDEX idx_role_permission_role_code ON role_permission (role_code);
CREATE INDEX idx_role_permission_permission_code ON role_permission (permission_code);
CREATE INDEX idx_account_permission_override_username ON account_permission_override (username);
CREATE INDEX idx_account_permission_override_permission_code ON account_permission_override (permission_code);

-- Permission indexes
CREATE INDEX idx_permission_module_code ON permission (module_code);
CREATE INDEX idx_permission_resource_code ON permission (resource_code);
CREATE INDEX idx_permission_action_code ON permission (action_code);
CREATE INDEX idx_permission_scope_code ON permission (scope_code);
CREATE INDEX idx_permission_resource_module_code ON permission_resource (module_code);

-- ============================================================================
-- END OF MIGRATION
-- ============================================================================

