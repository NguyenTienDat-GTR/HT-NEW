-- ============================================================================
-- V074: Danh mục cấp độ huynh trưởng đi riêng theo từng giáo phận (không dùng chung toàn hệ thống).
--       - leader_level có diocese_id; khóa chính (diocese_id, level_code); thứ tự duy nhất trong từng giáo phận.
--       - Danh mục hiện có được sao chép cho mọi giáo phận đang có (giữ nguyên tên, thứ tự, mô tả đã chỉnh).
--       - Mã cấp độ (level_code) vẫn là giá trị lưu ở huynh trưởng, loại khóa, lịch sử cấp bậc, hồ sơ tham dự;
--         nghĩa (tên, thứ tự) của mã được tra theo giáo phận của huynh trưởng / khóa. Vì mã không còn là khóa
--         duy nhất toàn cục nên các khóa ngoại tới leader_level(level_code) được gỡ, tính toàn vẹn do ứng dụng kiểm tra.
-- ============================================================================

ALTER TABLE leader DROP CONSTRAINT IF EXISTS fk_leader_leader_level;
ALTER TABLE training_course_type
    DROP CONSTRAINT IF EXISTS fk_course_type_required_level,
    DROP CONSTRAINT IF EXISTS fk_course_type_resulting_level;
ALTER TABLE rank_history
    DROP CONSTRAINT IF EXISTS fk_rank_history_old_level,
    DROP CONSTRAINT IF EXISTS fk_rank_history_new_level;
ALTER TABLE course_participation DROP CONSTRAINT IF EXISTS fk_participation_target_level;

ALTER TABLE leader_level DROP CONSTRAINT IF EXISTS leader_level_pkey;
ALTER TABLE leader_level DROP CONSTRAINT IF EXISTS uk_leader_level_order;
ALTER TABLE leader_level ADD COLUMN IF NOT EXISTS diocese_id UUID;

-- Sao chép danh mục chung hiện tại cho từng giáo phận.
INSERT INTO leader_level (
    level_code, level_name, display_order, is_system, description,
    created_at, created_by, updated_at, updated_by, status, diocese_id
)
SELECT lv.level_code, lv.level_name, lv.display_order, lv.is_system, lv.description,
       CURRENT_TIMESTAMP, 'SYSTEM', lv.updated_at, lv.updated_by, lv.status, d.id
FROM leader_level lv
         CROSS JOIN diocese d
WHERE lv.diocese_id IS NULL;

DELETE FROM leader_level WHERE diocese_id IS NULL;

ALTER TABLE leader_level ALTER COLUMN diocese_id SET NOT NULL;
ALTER TABLE leader_level ADD CONSTRAINT pk_leader_level PRIMARY KEY (diocese_id, level_code);
ALTER TABLE leader_level
    ADD CONSTRAINT fk_leader_level_diocese FOREIGN KEY (diocese_id) REFERENCES diocese (id) ON DELETE CASCADE;
-- Kiểm tra khi commit để đổi chỗ thứ tự trong cùng một giao dịch không vướng ràng buộc.
ALTER TABLE leader_level
    ADD CONSTRAINT uk_leader_level_order UNIQUE (diocese_id, display_order) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX IF NOT EXISTS idx_leader_level_code ON leader_level (level_code);
