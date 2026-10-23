-- Quyền tạo khóa huấn luyện quá khứ cho admin giáo hạt (khóa thuộc giáo hạt).
-- Không gán vào vai trò nào: super-admin cấp cho admin giáo phận (người xem được tài khoản admin giáo hạt), admin giáo phận
-- đang giữ quyền cùng loại cấp giáo phận (training.course.create_past.diocese) cấp lại cho admin giáo hạt thuộc giáo phận mình.
INSERT INTO permission (permission_code, module_code, resource_code, action_code, scope_code, created_at, created_by, updated_at, updated_by,
                        status, permission_name, description, is_system, display_order)
VALUES ('training.course.create_past.deanery', 'training', 'course', 'create_past', 'deanery',
        CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE,
        'Tạo khóa huấn luyện quá khứ - Giáo hạt',
        'Cho phép tài khoản giáo hạt tạo khóa huấn luyện trong quá khứ thuộc giáo hạt của mình, import danh sách tham dự (kèm thông tin để tạo huynh trưởng chưa có hồ sơ) từ Excel. Admin giáo phận giữ quyền cùng loại cấp giáo phận cấp cho admin giáo hạt.',
        TRUE, (SELECT COALESCE(max(display_order), 0) FROM permission) + 10)
ON CONFLICT (permission_code) DO NOTHING;
