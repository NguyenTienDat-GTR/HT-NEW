-- ============================================================================
-- V134: Chủ sở hữu loại khóa huấn luyện.
--       - owner_unit_type = DIOCESE: loại khóa do giáo phận tạo (loại khóa cấp giáo phận, hoặc loại khóa cấp giáo hạt
--         dùng chung cho mọi giáo hạt trong giáo phận). Giáo hạt chỉ được dùng, không sửa / đổi trạng thái.
--       - owner_unit_type = DEANERY: loại khóa cấp giáo hạt do một giáo hạt (owner_deanery_id) tự tạo; chỉ giáo hạt đó
--         thấy và sửa, giáo phận không thấy trong danh mục của mình.
--       Dữ liệu hiện có: loại khóa cấp giáo hạt thuộc về giáo hạt của tài khoản tạo ra nó khi (và chỉ khi) mọi khóa đang
--       dùng nó đều của giáo hạt đó và không bị loại khóa nào của cấp/giáo hạt khác lấy làm khóa tiên quyết.
--       Các loại còn lại giữ là loại khóa của giáo phận (dùng chung) để không làm khóa của giáo hạt khác mất loại khóa.
-- ============================================================================

ALTER TABLE training_course_type ADD COLUMN IF NOT EXISTS owner_unit_type VARCHAR(20) NOT NULL DEFAULT 'DIOCESE';
ALTER TABLE training_course_type ADD COLUMN IF NOT EXISTS owner_deanery_id UUID;

UPDATE training_course_type t
SET owner_unit_type  = 'DEANERY',
    owner_deanery_id = a.deanery_id
FROM account a
         JOIN deanery de ON de.id = a.deanery_id
WHERE t.management_unit_type = 'DEANERY'
  AND a.username = t.created_by
  AND de.diocese_id = t.diocese_id
  AND NOT EXISTS (SELECT 1
                  FROM training_course c
                  WHERE c.course_type_code = t.type_code
                    AND c.deanery_id IS DISTINCT FROM a.deanery_id)
  AND NOT EXISTS (SELECT 1
                  FROM training_course_type p
                           LEFT JOIN account pa ON pa.username = p.created_by
                  WHERE p.prerequisite_type_code = t.type_code
                    AND (p.management_unit_type = 'DIOCESE' OR pa.deanery_id IS DISTINCT FROM a.deanery_id));

ALTER TABLE training_course_type
    ADD CONSTRAINT fk_training_course_type_owner_deanery FOREIGN KEY (owner_deanery_id) REFERENCES deanery (id);
ALTER TABLE training_course_type
    ADD CONSTRAINT chk_training_course_type_owner CHECK (
        (owner_unit_type = 'DEANERY') = (owner_deanery_id IS NOT NULL)
            AND (owner_unit_type = 'DIOCESE' OR management_unit_type = 'DEANERY'));
CREATE INDEX IF NOT EXISTS idx_training_course_type_owner_deanery ON training_course_type (owner_deanery_id);
