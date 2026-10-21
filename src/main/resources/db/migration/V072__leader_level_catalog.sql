-- ============================================================================
-- V072: Danh mục cấp độ huynh trưởng (thay cho enum LeaderLevel).
--       - Bảng leader_level: mã, tên, thứ tự (display_order càng lớn cấp càng cao).
--       - Cấp trước trong thứ tự là điều kiện để đạt cấp kế tiếp.
--       - Các cột đang lưu mã cấp (leader, loại khóa, lịch sử cấp bậc) giữ nguyên giá trị, thêm khóa ngoại.
--       - Hồ sơ tham dự ngoại lệ có cấp độ đạt được do người duyệt chọn (target_level).
--       - Lịch sử cấp bậc gắn với khóa để thu hồi chứng nhận hạ về đúng cấp trước đó.
-- ============================================================================

CREATE TABLE leader_level
(
    level_code    VARCHAR(50) PRIMARY KEY,
    level_name    VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL,
    -- Cấp hệ thống (NONE - chưa có cấp): luôn thấp nhất, không xóa / không đổi thứ tự.
    is_system     BOOLEAN      NOT NULL DEFAULT FALSE,
    description   TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    VARCHAR(100),
    status        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Kiểm tra khi commit để đổi chỗ thứ tự trong cùng một giao dịch không vướng ràng buộc.
    CONSTRAINT uk_leader_level_order UNIQUE (display_order) DEFERRABLE INITIALLY DEFERRED
);

INSERT INTO leader_level (level_code, level_name, display_order, is_system, created_by)
VALUES ('NONE', 'Chưa có cấp', 0, TRUE, 'SYSTEM'),
       ('HT_XU', 'Huynh trưởng xứ', 1, FALSE, 'SYSTEM'),
       ('DU_TRUONG', 'Dự trưởng', 2, FALSE, 'SYSTEM'),
       ('HT_I', 'Huynh trưởng cấp I', 3, FALSE, 'SYSTEM'),
       ('HT_II', 'Huynh trưởng cấp II', 4, FALSE, 'SYSTEM'),
       ('HT_III', 'Huynh trưởng cấp III', 5, FALSE, 'SYSTEM'),
       ('HLV_I', 'Huấn luyện viên cấp I', 6, FALSE, 'SYSTEM'),
       ('HLV_II', 'Huấn luyện viên cấp II', 7, FALSE, 'SYSTEM'),
       ('HLV_III', 'Huấn luyện viên cấp III', 8, FALSE, 'SYSTEM');

-- Dữ liệu cũ lưu đúng mã enum nên map thẳng; mã lạ (nếu có) đưa về NONE / NULL trước khi thêm khóa ngoại.
UPDATE leader SET leader_level = 'NONE' WHERE leader_level NOT IN (SELECT level_code FROM leader_level);
UPDATE training_course_type SET required_current_level = NULL
WHERE required_current_level IS NOT NULL AND required_current_level NOT IN (SELECT level_code FROM leader_level);
UPDATE training_course_type SET resulting_level = NULL
WHERE resulting_level IS NOT NULL AND resulting_level NOT IN (SELECT level_code FROM leader_level);
UPDATE rank_history SET old_level = NULL
WHERE old_level IS NOT NULL AND old_level NOT IN (SELECT level_code FROM leader_level);
UPDATE rank_history SET new_level = NULL
WHERE new_level IS NOT NULL AND new_level NOT IN (SELECT level_code FROM leader_level);

ALTER TABLE leader
    ADD CONSTRAINT fk_leader_leader_level FOREIGN KEY (leader_level) REFERENCES leader_level (level_code);
ALTER TABLE training_course_type
    ADD CONSTRAINT fk_course_type_required_level FOREIGN KEY (required_current_level) REFERENCES leader_level (level_code),
    ADD CONSTRAINT fk_course_type_resulting_level FOREIGN KEY (resulting_level) REFERENCES leader_level (level_code);
ALTER TABLE rank_history
    ADD CONSTRAINT fk_rank_history_old_level FOREIGN KEY (old_level) REFERENCES leader_level (level_code),
    ADD CONSTRAINT fk_rank_history_new_level FOREIGN KEY (new_level) REFERENCES leader_level (level_code);

-- Cấp độ đạt được của hồ sơ ngoại lệ (người duyệt chọn); NULL = theo cấp độ đạt được của loại khóa.
ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS target_level VARCHAR(50),
    ADD CONSTRAINT fk_participation_target_level FOREIGN KEY (target_level) REFERENCES leader_level (level_code);

-- Lịch sử cấp bậc gắn với khóa sinh ra nó; reverted = đã bị hạ cấp khi thu hồi chứng nhận.
ALTER TABLE rank_history
    ADD COLUMN IF NOT EXISTS course_code VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reverted BOOLEAN NOT NULL DEFAULT FALSE;

-- Gắn các lần thăng cấp cũ (ghi chú "Thăng cấp khi kết thúc khóa <mã khóa>") với khóa tương ứng.
UPDATE rank_history r
SET course_code = substring(r.note FROM 'Thăng cấp khi kết thúc khóa (.+)$')
WHERE r.course_code IS NULL
  AND r.note LIKE 'Thăng cấp khi kết thúc khóa %'
  AND EXISTS (SELECT 1 FROM training_course c WHERE c.course_code = substring(r.note FROM 'Thăng cấp khi kết thúc khóa (.+)$'));

-- ----------------------------------------------------------------------------
-- Quyền quản lý danh mục cấp độ (chỉ super admin).
-- ----------------------------------------------------------------------------
INSERT INTO permission_resource (
    resource_code, module_code, resource_name, description, display_order,
    status, created_at, created_by, updated_at, updated_by
)
VALUES ('leader_level', 'system', 'Leader level', 'Danh mục cấp độ huynh trưởng và thứ tự cấp bậc', 56,
        TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (resource_code) DO UPDATE
SET module_code = EXCLUDED.module_code, resource_name = EXCLUDED.resource_name, description = EXCLUDED.description,
    status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO permission (
    permission_code, permission_name, module_code, resource_code, action_code, scope_code,
    description, is_system, display_order, status, created_at, created_by, updated_at, updated_by
)
VALUES
    ('system.leader_level.read.all', 'Xem cấp độ huynh trưởng', 'system', 'leader_level', 'read', 'all',
     'Xem danh mục cấp độ huynh trưởng', TRUE, 560, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.leader_level.create.all', 'Tạo cấp độ huynh trưởng', 'system', 'leader_level', 'create', 'all',
     'Thêm cấp độ mới vào danh mục', TRUE, 561, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.leader_level.update.all', 'Sửa và sắp xếp cấp độ huynh trưởng', 'system', 'leader_level', 'update', 'all',
     'Đổi tên và kéo thả thứ tự cấp độ', TRUE, 562, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('system.leader_level.delete.all', 'Xóa cấp độ huynh trưởng', 'system', 'leader_level', 'delete', 'all',
     'Xóa cấp độ chưa được sử dụng', TRUE, 563, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name, description = EXCLUDED.description, status = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';

INSERT INTO role_permission (
    id, role_code, permission_code, effect, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by
)
SELECT
    (
        substr(md5(r.role_code || ':' || p.permission_code), 1, 8) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 9, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 13, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 17, 4) || '-' ||
        substr(md5(r.role_code || ':' || p.permission_code), 21, 12)
    )::uuid,
    r.role_code, p.permission_code, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role r
JOIN permission p ON p.resource_code = 'leader_level'
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_code, permission_code) DO UPDATE
SET effect = 'ALLOW', expires_at = NULL, status = TRUE, updated_at = CURRENT_TIMESTAMP, updated_by = 'SYSTEM';
