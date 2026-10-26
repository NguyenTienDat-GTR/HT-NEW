-- ============================================================================
-- V104: Khóa giáo phận dành cho giáo hạt (đợt), gửi hồ sơ liên giáo hạt cho khóa giáo hạt và ghi chú hồ sơ đăng ký.
--       - training_course_target_deanery: các giáo hạt mà khóa giáo phận dành cho (rỗng = mở cho tất cả).
--       - course_participation: lí do gửi ngoài đợt, ghi chú của người gửi, thông tin liên giáo hạt.
--       - cross_deanery_request(_item): yêu cầu gửi hồ sơ sang khóa của giáo hạt khác, chờ giáo hạt nhà đồng ý.
--       - Quyền training.participation.cross_approve.deanery: đồng ý / từ chối yêu cầu liên giáo hạt của giáo xứ trong giáo hạt mình.
-- ============================================================================

CREATE TABLE IF NOT EXISTS training_course_target_deanery (
    course_code VARCHAR(255) NOT NULL,
    deanery_id  UUID         NOT NULL,
    PRIMARY KEY (course_code, deanery_id),
    CONSTRAINT fk_training_course_target_deanery_course FOREIGN KEY (course_code) REFERENCES training_course (course_code) ON DELETE CASCADE,
    CONSTRAINT fk_training_course_target_deanery_deanery FOREIGN KEY (deanery_id) REFERENCES deanery (id) ON DELETE CASCADE
);

ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS off_target_reason VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS registration_note VARCHAR(1000) NULL,
    ADD COLUMN IF NOT EXISTS cross_deanery_reason VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS home_deanery_id UUID NULL,
    ADD COLUMN IF NOT EXISTS home_deanery_approved_by VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS home_deanery_approved_at TIMESTAMP WITH TIME ZONE NULL;

CREATE TABLE IF NOT EXISTS cross_deanery_request (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_code      VARCHAR(255) NOT NULL,
    home_deanery_id  UUID         NOT NULL,
    parish_id        UUID         NULL,
    requester_type   VARCHAR(20)  NOT NULL,
    request_status   VARCHAR(20)  NOT NULL,
    decision_note    VARCHAR(500) NULL,
    decided_by       VARCHAR(100) NULL,
    decided_at       TIMESTAMP WITH TIME ZONE NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(100) NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NULL,
    updated_by       VARCHAR(100) NULL,
    status           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cross_deanery_request_course FOREIGN KEY (course_code) REFERENCES training_course (course_code) ON DELETE CASCADE,
    CONSTRAINT fk_cross_deanery_request_home_deanery FOREIGN KEY (home_deanery_id) REFERENCES deanery (id)
);
CREATE INDEX IF NOT EXISTS idx_cross_deanery_request_home ON cross_deanery_request (home_deanery_id, request_status);
CREATE INDEX IF NOT EXISTS idx_cross_deanery_request_course ON cross_deanery_request (course_code);

CREATE TABLE IF NOT EXISTS cross_deanery_request_item (
    id               VARCHAR(36)   NOT NULL PRIMARY KEY,
    request_id       VARCHAR(36)   NOT NULL,
    leader_id        UUID          NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    exception_reason VARCHAR(500)  NULL,
    note             VARCHAR(1000) NULL,
    item_status      VARCHAR(20)   NOT NULL,
    participation_id VARCHAR(255)  NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       VARCHAR(100)  NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NULL,
    updated_by       VARCHAR(100)  NULL,
    status           BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cross_deanery_item_request FOREIGN KEY (request_id) REFERENCES cross_deanery_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_cross_deanery_item_leader FOREIGN KEY (leader_id) REFERENCES leader (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_cross_deanery_item_request ON cross_deanery_request_item (request_id);
CREATE INDEX IF NOT EXISTS idx_cross_deanery_item_leader ON cross_deanery_request_item (leader_id, item_status);

INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('cross_approve', 'Đồng ý gửi liên giáo hạt', 'Đồng ý hoặc từ chối yêu cầu gửi hồ sơ sang khóa của giáo hạt khác', 80, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO UPDATE SET status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('training.participation.cross_approve.deanery', 'Đồng ý gửi liên giáo hạt - Giáo hạt', 'training', 'participation', 'cross_approve', 'deanery',
     'Đồng ý hoặc từ chối yêu cầu của giáo xứ trong giáo hạt muốn gửi hồ sơ sang khóa của giáo hạt khác', TRUE, 550, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, action_code = EXCLUDED.action_code,
    is_system = TRUE, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO role_permission (id, role_code, permission_code, created_at, created_by, updated_at, updated_by, status, assigned_by, assigned_at)
SELECT gen_random_uuid()::text, r.role_code, p.permission_code, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE, 'SYSTEM', CURRENT_TIMESTAMP
FROM role r
JOIN permission p ON p.permission_code = 'training.participation.cross_approve.deanery'
WHERE r.role_code = 'ADMIN_DEANERY'
ON CONFLICT (role_code, permission_code) DO UPDATE
SET expires_at = NULL, revoked_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
