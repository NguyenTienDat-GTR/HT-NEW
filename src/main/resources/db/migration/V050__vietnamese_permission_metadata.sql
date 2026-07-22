WITH module_labels(module_code, module_name, description) AS (
    VALUES
        ('auth', 'Xác thực', 'Nhóm quyền liên quan đến đăng nhập, phiên và bảo mật tài khoản.'),
        ('analytics', 'Phân tích', 'Nhóm quyền xem dashboard và số liệu phân tích theo phạm vi.'),
        ('organization', 'Tổ chức', 'Nhóm quyền quản lý cơ cấu giáo phận, giáo hạt, giáo xứ và nhân sự.'),
        ('training', 'Huấn luyện', 'Nhóm quyền quản lý khóa huấn luyện, tham dự, điểm số và chứng nhận.'),
        ('system', 'Hệ thống', 'Nhóm quyền quản trị tài khoản, vai trò, phân quyền và nhật ký hệ thống.')
)
UPDATE permission_module pm
SET module_name = ml.module_name,
    description = ml.description,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM module_labels ml
WHERE pm.module_code = ml.module_code;

WITH resource_labels(resource_code, resource_name, description) AS (
    VALUES
        ('session', 'Phiên đăng nhập', 'Phiên xác thực và hoạt động đăng nhập của tài khoản.'),
        ('dashboard', 'Dashboard phân tích', 'Dashboard phân tích tổng quan và số liệu vận hành theo phạm vi.'),
        ('diocese', 'Giáo phận', 'Thông tin và cơ cấu cấp giáo phận.'),
        ('deanery', 'Giáo hạt', 'Thông tin và cơ cấu cấp giáo hạt.'),
        ('parish', 'Giáo xứ', 'Thông tin và cơ cấu cấp giáo xứ.'),
        ('leader', 'Huynh trưởng', 'Hồ sơ huynh trưởng và thông tin nhân sự.'),
        ('position', 'Chức vụ', 'Danh mục chức vụ áp dụng cho ban điều hành.'),
        ('executive_board_assignment', 'Phân công ban điều hành', 'Phân công chức vụ ban điều hành theo đơn vị.'),
        ('requirement', 'Điều kiện khóa', 'Điều kiện và yêu cầu đầu vào của khóa huấn luyện.'),
        ('course', 'Khóa huấn luyện', 'Thông tin khóa huấn luyện và cấu hình tổ chức khóa.'),
        ('participation', 'Tham dự khóa', 'Hồ sơ đăng ký, duyệt, tham dự và chấm điểm khóa huấn luyện.'),
        ('score_component', 'Thành phần điểm', 'Danh mục thành phần điểm dùng trong công thức chấm điểm.'),
        ('score_formula', 'Công thức điểm', 'Công thức tính điểm cho khóa huấn luyện.'),
        ('certificate', 'Chứng nhận', 'Hồ sơ chứng nhận sau khóa huấn luyện.'),
        ('account', 'Tài khoản', 'Tài khoản đăng nhập và phạm vi quản lý.'),
        ('account_role', 'Vai trò tài khoản', 'Quan hệ gán vai trò cho tài khoản.'),
        ('role', 'Vai trò', 'Vai trò nghiệp vụ và vai trò hệ thống.'),
        ('permission', 'Quyền hạn', 'Danh mục quyền hệ thống được sinh từ module, đối tượng, thao tác và phạm vi.'),
        ('role_permission', 'Phân quyền vai trò', 'Ma trận quyền được gán cho từng vai trò.'),
        ('account_permission', 'Quyền riêng tài khoản', 'Quyền cho phép hoặc từ chối trực tiếp trên từng tài khoản.'),
        ('audit_log', 'Nhật ký hệ thống', 'Nhật ký thao tác và sự kiện quản trị trong hệ thống.'),
        ('notification', 'Thông báo', 'Thông báo nghiệp vụ và trạng thái đọc của người dùng.')
)
UPDATE permission_resource pr
SET resource_name = rl.resource_name,
    description = rl.description,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM resource_labels rl
WHERE pr.resource_code = rl.resource_code;

WITH action_labels(action_code, action_name, description) AS (
    VALUES
        ('create', 'Tạo', 'Tạo bản ghi mới.'),
        ('read', 'Xem', 'Xem danh sách và chi tiết bản ghi.'),
        ('update', 'Cập nhật', 'Cập nhật thông tin bản ghi hiện có.'),
        ('delete', 'Xóa', 'Xóa bản ghi.'),
        ('toggle', 'Đổi trạng thái', 'Bật hoặc tạm ngưng bản ghi hiện có.'),
        ('submit', 'Gửi', 'Gửi hồ sơ hoặc yêu cầu vào luồng xử lý.'),
        ('approve', 'Duyệt', 'Duyệt hồ sơ hoặc bước nghiệp vụ.'),
        ('score', 'Chấm điểm', 'Nhập hoặc cập nhật điểm trong luồng huấn luyện.'),
        ('assign', 'Gán', 'Gán quan hệ quyền hoặc vai trò.'),
        ('revoke', 'Thu hồi', 'Thu hồi quan hệ quyền đã gán.'),
        ('manage', 'Quản lý', 'Thực hiện nhóm thao tác quản trị trên đối tượng.'),
        ('export', 'Xuất dữ liệu', 'Xuất dữ liệu hoặc báo cáo.')
)
UPDATE permission_action pa
SET action_name = al.action_name,
    description = al.description,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM action_labels al
WHERE pa.action_code = al.action_code;

WITH scope_labels(scope_code, scope_name, description) AS (
    VALUES
        ('own', 'Dữ liệu sở hữu', 'Chỉ áp dụng cho bản ghi do tài khoản hiện tại sở hữu.'),
        ('parish', 'Giáo xứ hiện tại', 'Áp dụng trong giáo xứ gắn với tài khoản hiện tại.'),
        ('deanery', 'Giáo hạt hiện tại', 'Áp dụng trong giáo hạt gắn với tài khoản hiện tại.'),
        ('diocese', 'Giáo phận hiện tại', 'Áp dụng trong giáo phận gắn với tài khoản hiện tại.'),
        ('all', 'Toàn hệ thống', 'Áp dụng cho toàn bộ dữ liệu trong hệ thống.')
)
UPDATE permission_scope ps
SET scope_name = sl.scope_name,
    description = sl.description,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM scope_labels sl
WHERE ps.scope_code = sl.scope_code;

WITH action_terms(action_code, action_label, action_phrase) AS (
    VALUES
        ('create', 'Tạo', 'tạo'),
        ('read', 'Xem', 'xem'),
        ('update', 'Cập nhật', 'cập nhật'),
        ('delete', 'Xóa', 'xóa'),
        ('toggle', 'Đổi trạng thái', 'đổi trạng thái'),
        ('submit', 'Gửi', 'gửi'),
        ('approve', 'Duyệt', 'duyệt'),
        ('score', 'Chấm điểm', 'chấm điểm'),
        ('assign', 'Gán', 'gán'),
        ('revoke', 'Thu hồi', 'thu hồi'),
        ('manage', 'Quản lý', 'quản lý'),
        ('export', 'Xuất dữ liệu', 'xuất dữ liệu')
),
resource_terms(resource_code, object_label, object_phrase) AS (
    VALUES
        ('session', 'phiên đăng nhập', 'phiên đăng nhập'),
        ('dashboard', 'dashboard phân tích', 'dashboard phân tích'),
        ('diocese', 'giáo phận', 'giáo phận'),
        ('deanery', 'giáo hạt', 'giáo hạt'),
        ('parish', 'giáo xứ', 'giáo xứ'),
        ('leader', 'hồ sơ huynh trưởng', 'hồ sơ huynh trưởng'),
        ('position', 'chức vụ', 'chức vụ'),
        ('executive_board_assignment', 'phân công ban điều hành', 'phân công ban điều hành'),
        ('requirement', 'điều kiện khóa huấn luyện', 'điều kiện khóa huấn luyện'),
        ('course', 'khóa huấn luyện', 'khóa huấn luyện'),
        ('participation', 'hồ sơ tham dự khóa', 'hồ sơ tham dự khóa'),
        ('score_component', 'thành phần điểm', 'thành phần điểm'),
        ('score_formula', 'công thức điểm', 'công thức điểm'),
        ('certificate', 'chứng nhận huấn luyện', 'chứng nhận huấn luyện'),
        ('account', 'tài khoản', 'tài khoản'),
        ('account_role', 'vai trò tài khoản', 'vai trò tài khoản'),
        ('role', 'vai trò', 'vai trò'),
        ('permission', 'quyền hạn', 'quyền hạn'),
        ('role_permission', 'phân quyền vai trò', 'phân quyền vai trò'),
        ('account_permission', 'quyền riêng tài khoản', 'quyền riêng tài khoản'),
        ('audit_log', 'nhật ký hệ thống', 'nhật ký hệ thống'),
        ('notification', 'thông báo', 'thông báo')
),
scope_terms(scope_code, scope_label, scope_phrase) AS (
    VALUES
        ('own', 'Dữ liệu sở hữu', 'dữ liệu do tài khoản hiện tại sở hữu'),
        ('parish', 'Giáo xứ hiện tại', 'giáo xứ gắn với tài khoản hiện tại'),
        ('deanery', 'Giáo hạt hiện tại', 'giáo hạt gắn với tài khoản hiện tại'),
        ('diocese', 'Giáo phận hiện tại', 'giáo phận gắn với tài khoản hiện tại'),
        ('all', 'Toàn hệ thống', 'toàn bộ hệ thống')
),
permission_labels AS (
    SELECT
        p.permission_code,
        CASE
            WHEN p.permission_code = 'auth.session.manage.all' THEN 'Quản lý phiên đăng nhập - Toàn hệ thống'
            WHEN p.resource_code = 'dashboard' AND p.action_code = 'read' THEN 'Xem dashboard phân tích - ' || st.scope_label
            WHEN p.resource_code = 'role_permission' AND p.action_code = 'assign' THEN 'Gán quyền cho vai trò - ' || st.scope_label
            WHEN p.resource_code = 'role_permission' AND p.action_code = 'revoke' THEN 'Thu hồi quyền khỏi vai trò - ' || st.scope_label
            WHEN p.resource_code = 'role_permission' AND p.action_code = 'read' THEN 'Xem phân quyền vai trò - ' || st.scope_label
            WHEN p.resource_code = 'account_role' AND p.action_code = 'assign' THEN 'Gán vai trò cho tài khoản - ' || st.scope_label
            WHEN p.resource_code = 'account_permission' AND p.action_code = 'manage' THEN 'Quản lý quyền riêng tài khoản - ' || st.scope_label
            ELSE at.action_label || ' ' || rt.object_label || ' - ' || st.scope_label
        END AS permission_name,
        CASE
            WHEN p.permission_code = 'auth.session.manage.all' THEN 'Cho phép quản lý phiên đăng nhập và kiểm tra thông tin quản trị trên toàn hệ thống.'
            WHEN p.resource_code = 'dashboard' AND p.action_code = 'read' THEN 'Cho phép xem dashboard phân tích trong phạm vi ' || st.scope_phrase || '.'
            WHEN p.resource_code = 'role_permission' AND p.action_code = 'assign' THEN 'Cho phép gán các quyền phù hợp cho vai trò trong phạm vi ' || st.scope_phrase || '.'
            WHEN p.resource_code = 'role_permission' AND p.action_code = 'revoke' THEN 'Cho phép thu hồi quyền đã gán khỏi vai trò trong phạm vi ' || st.scope_phrase || '.'
            WHEN p.resource_code = 'role_permission' AND p.action_code = 'read' THEN 'Cho phép xem ma trận quyền của vai trò trong phạm vi ' || st.scope_phrase || '.'
            WHEN p.resource_code = 'account_role' AND p.action_code = 'assign' THEN 'Cho phép gán vai trò phù hợp cho tài khoản trong phạm vi ' || st.scope_phrase || '.'
            WHEN p.resource_code = 'account_permission' AND p.action_code = 'manage' THEN 'Cho phép quản lý quyền cho phép hoặc từ chối trực tiếp trên tài khoản trong phạm vi ' || st.scope_phrase || '.'
            ELSE 'Cho phép ' || at.action_phrase || ' ' || rt.object_phrase || ' trong phạm vi ' || st.scope_phrase || '.'
        END AS description
    FROM permission p
    JOIN action_terms at ON at.action_code = p.action_code
    JOIN resource_terms rt ON rt.resource_code = p.resource_code
    JOIN scope_terms st ON st.scope_code = p.scope_code
)
UPDATE permission p
SET permission_name = pl.permission_name,
    description = pl.description,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'SYSTEM'
FROM permission_labels pl
WHERE p.permission_code = pl.permission_code;
