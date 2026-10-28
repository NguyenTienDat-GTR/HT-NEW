-- Giáo họ / giáo điểm là đơn vị cơ sở thuộc một giáo xứ, lưu cùng bảng parish.
-- Huynh trưởng và tài khoản gắn parish_id vào chính giáo họ/giáo điểm nên phạm vi quyền của giáo xứ cha
-- không bao trùm giáo họ/giáo điểm; deanery_id của giáo họ/giáo điểm bằng giáo hạt của giáo xứ cha.
ALTER TABLE parish
    ADD COLUMN unit_type VARCHAR(20) NOT NULL DEFAULT 'PARISH',
    ADD COLUMN parent_parish_id UUID NULL;

ALTER TABLE parish
    ADD CONSTRAINT fk_parish_parent_parish_id FOREIGN KEY (parent_parish_id) REFERENCES parish (id),
    ADD CONSTRAINT chk_parish_unit_type CHECK (unit_type IN ('PARISH', 'GIAO_HO', 'GIAO_DIEM')),
    ADD CONSTRAINT chk_parish_parent_by_type CHECK (
        (unit_type = 'PARISH' AND parent_parish_id IS NULL)
        OR (unit_type <> 'PARISH' AND parent_parish_id IS NOT NULL AND parent_parish_id <> id)
    );

CREATE INDEX idx_parish_parent_parish_id ON parish (parent_parish_id);
