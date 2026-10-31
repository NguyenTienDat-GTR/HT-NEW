-- Chức vụ áp dụng riêng cho từng giáo phận (trước đây dùng chung toàn hệ thống).
-- Mỗi chức vụ cũ được nhân bản cho từng giáo phận; các phân công ban điều hành được chuyển sang bản của giáo phận chứa đơn vị.
ALTER TABLE "position" ADD COLUMN IF NOT EXISTS diocese_id UUID;

CREATE TEMP TABLE position_clone ON COMMIT DROP AS
SELECT p.position_code AS old_code, d.id AS diocese_id, gen_random_uuid()::text AS new_code
FROM "position" p
CROSS JOIN diocese d
WHERE p.diocese_id IS NULL;

INSERT INTO "position" (position_code, created_at, created_by, updated_at, updated_by, status, position_name, position_type, display_order, diocese_id)
SELECT c.new_code, p.created_at, p.created_by, p.updated_at, p.updated_by, p.status, p.position_name, p.position_type, p.display_order, c.diocese_id
FROM position_clone c
JOIN "position" p ON p.position_code = c.old_code;

UPDATE executive_board_assignment a
SET position_id = c.new_code
FROM position_clone c
WHERE a.position_id = c.old_code
  AND c.diocese_id = (
      CASE a.unit_type
          WHEN 'DIOCESE' THEN a.unit_id
          WHEN 'DEANERY' THEN (SELECT de.diocese_id FROM deanery de WHERE de.id = a.unit_id)
          WHEN 'PARISH' THEN (SELECT de.diocese_id FROM parish pa JOIN deanery de ON de.id = pa.deanery_id WHERE pa.id = a.unit_id)
      END
  );

-- Bản chung cũ không còn dùng; phân công không xác định được giáo phận (nếu có) tự về NULL theo khóa ngoại.
DELETE FROM "position" WHERE diocese_id IS NULL;

ALTER TABLE "position" ALTER COLUMN diocese_id SET NOT NULL;
ALTER TABLE "position"
    ADD CONSTRAINT fk_position_diocese_id FOREIGN KEY (diocese_id) REFERENCES diocese (id);
CREATE INDEX IF NOT EXISTS idx_position_diocese_type ON "position" (diocese_id, position_type);
