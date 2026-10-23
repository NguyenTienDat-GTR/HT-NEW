-- Khóa huấn luyện trong quá khứ: tạo thủ công (không loại khóa, không thời gian đăng ký), sau đó import danh sách tham dự từ Excel.
-- Chỉ tài khoản được super-admin cấp riêng quyền này mới dùng được; KHÔNG gán vào vai trò nào.

-- 1) Hành động mới để mã quyền không trùng tiền tố 'training.course.create.' (tiền tố hiện bật nút "Tạo khóa").
INSERT INTO permission_action (action_code, action_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('create_past', 'Tạo khóa quá khứ', 'Tạo khóa huấn luyện trong quá khứ và nhập danh sách tham dự từ Excel', 70,
        TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (action_code) DO NOTHING;

-- 2) Quyền riêng, chỉ super-admin cấp trực tiếp cho tài khoản qua màn "Phân quyền tài khoản".
INSERT INTO permission (permission_code, module_code, resource_code, action_code, scope_code, created_at, created_by, updated_at, updated_by,
                        status, permission_name, description, is_system, display_order)
VALUES ('training.course.create_past.diocese', 'training', 'course', 'create_past', 'diocese',
        CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE,
        'Tạo khóa huấn luyện quá khứ - Giáo phận',
        'Cho phép tài khoản giáo phận tạo khóa huấn luyện trong quá khứ (chỉ cần năm tổ chức), import danh sách tham dự kèm điểm và kết quả từ Excel. Chỉ super-admin cấp cho từng tài khoản.',
        TRUE, (SELECT COALESCE(max(display_order), 0) FROM permission) + 10)
ON CONFLICT (permission_code) DO NOTHING;

-- 3) Dữ liệu khóa quá khứ.
ALTER TABLE training_course ALTER COLUMN course_type_code DROP NOT NULL;
ALTER TABLE training_course ADD COLUMN IF NOT EXISTS is_past_course BOOLEAN NOT NULL DEFAULT FALSE;
-- Cấp độ đạt được của khóa quá khứ (khóa thường lấy theo loại khóa).
ALTER TABLE training_course ADD COLUMN IF NOT EXISTS resulting_level VARCHAR(50);

-- Khóa thường luôn có loại khóa; chỉ khóa quá khứ được để trống.
ALTER TABLE training_course DROP CONSTRAINT IF EXISTS chk_training_course_type_required;
ALTER TABLE training_course ADD CONSTRAINT chk_training_course_type_required
    CHECK (is_past_course OR course_type_code IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_training_course_past ON training_course (is_past_course) WHERE is_past_course;
