-- Chức vụ thuộc riêng từng đơn vị (giáo phận, giáo hạt, giáo xứ): mỗi đơn vị tự tạo chức vụ cho cấp của mình.
-- Chức vụ cấp giáo phận thuộc giáo phận đang sở hữu. Chức vụ cấp giáo hạt / giáo xứ trước đây dùng chung cả giáo phận
-- nên được nhân bản cho từng giáo hạt / giáo xứ hiện có; phân công ban điều hành chuyển sang bản của đúng đơn vị.
ALTER TABLE "position" ADD COLUMN IF NOT EXISTS unit_id UUID;

UPDATE "position" SET unit_id = diocese_id
WHERE unit_id IS NULL AND position_type IS DISTINCT FROM 'DEANERY' AND position_type IS DISTINCT FROM 'PARISH';

CREATE TEMP TABLE position_unit_clone ON COMMIT DROP AS
SELECT p.position_code AS old_code, 'DEANERY'::text AS unit_type, de.id AS unit_id, gen_random_uuid()::text AS new_code
FROM "position" p
JOIN deanery de ON de.diocese_id = p.diocese_id
WHERE p.unit_id IS NULL AND p.position_type = 'DEANERY'
UNION ALL
SELECT p.position_code, 'PARISH', pa.id, gen_random_uuid()::text
FROM "position" p
JOIN deanery de ON de.diocese_id = p.diocese_id
JOIN parish pa ON pa.deanery_id = de.id
WHERE p.unit_id IS NULL AND p.position_type = 'PARISH';

INSERT INTO "position" (position_code, created_at, created_by, updated_at, updated_by, status, position_name, position_type, display_order, diocese_id, unit_id)
SELECT c.new_code, p.created_at, p.created_by, p.updated_at, p.updated_by, p.status, p.position_name, p.position_type, p.display_order, p.diocese_id, c.unit_id
FROM position_unit_clone c
JOIN "position" p ON p.position_code = c.old_code;

UPDATE executive_board_assignment a
SET position_id = c.new_code
FROM position_unit_clone c
WHERE a.position_id = c.old_code
  AND a.unit_type = c.unit_type
  AND a.unit_id = c.unit_id;

-- Bản dùng chung cũ không còn dùng; phân công không khớp đơn vị nào (nếu có) tự về NULL theo khóa ngoại.
DELETE FROM "position" WHERE unit_id IS NULL;

ALTER TABLE "position" ALTER COLUMN unit_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_position_unit_type ON "position" (unit_id, position_type);
