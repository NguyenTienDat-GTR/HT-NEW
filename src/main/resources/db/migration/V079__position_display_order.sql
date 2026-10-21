-- Thứ tự hiển thị chức vụ trong ban điều hành của đơn vị (linh mục tuyên úy luôn là thứ tự 0, chức vụ bắt đầu từ 1).
ALTER TABLE position ADD COLUMN IF NOT EXISTS display_order INTEGER;

UPDATE position p
SET display_order = ranked.rn
FROM (
    SELECT position_code,
           row_number() OVER (PARTITION BY position_type ORDER BY created_at, position_name) AS rn
    FROM position
) ranked
WHERE ranked.position_code = p.position_code
  AND p.display_order IS NULL;

ALTER TABLE position ALTER COLUMN display_order SET DEFAULT 1;
ALTER TABLE position ALTER COLUMN display_order SET NOT NULL;
