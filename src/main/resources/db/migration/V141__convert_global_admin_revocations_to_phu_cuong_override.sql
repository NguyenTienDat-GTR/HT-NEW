-- Chuyển các thu hồi quyền toàn hệ thống đã làm trên production (trước khi có ngoại lệ theo đơn vị) cho đúng phạm vi đã chủ ý.
--
-- Bối cảnh: bản cũ chỉ thu hồi được quyền của vai trò cho TẤT CẢ giáo phận (role_permission.revoked_at). Chủ hệ thống đã thu hồi
-- một số quyền của Admin giáo phận / giáo hạt / giáo xứ (ví dụ quyền tạo huynh trưởng) nhưng chỉ chủ ý cho giáo phận Phú Cường.
-- Quyết định đã chốt: giữ nguyên thu hồi ở Phú Cường, còn bộ quyền mặc định chung (và các giáo phận khác) có lại các quyền đó.
--
-- Với mỗi bản ghi role_permission của ADMIN_DIOCESE / ADMIN_DEANERY / ADMIN_PARISH đã bị thu hồi THỦ CÔNG:
--   1. thêm thu hồi riêng (EXCLUDE) cho giáo phận Phú Cường, ghi lại người và thời điểm thu hồi cũ;
--   2. khôi phục bản ghi mặc định (bỏ dấu thu hồi).
-- Không đụng tới: quyền hết hạn (EXPIRED), vai trò khác (vai trò tự tạo của từng đơn vị), bản ghi đang hiệu lực, mọi bảng khác.
-- Chỉ chạy khi tìm thấy đúng một giáo phận tên Phú Cường; nếu không thì giữ nguyên toàn bộ (tránh vô tình cấp lại quyền cho mọi nơi).

DO
$$
DECLARE
    v_diocese_id UUID;
    v_matches    INTEGER;
    v_converted  INTEGER;
BEGIN
    SELECT count(*), max(id::text)::uuid
    INTO v_matches, v_diocese_id
    FROM diocese
    WHERE lower(btrim(name)) = lower('Phú Cường');

    IF v_matches <> 1 THEN
        RAISE NOTICE 'V141 bỏ qua: tìm thấy % giáo phận tên Phú Cường (cần đúng 1); các thu hồi cũ được giữ nguyên.', v_matches;
        RETURN;
    END IF;

    CREATE TEMP TABLE v141_revoked ON COMMIT DROP AS
    SELECT rp.id, rp.role_code, rp.permission_code, rp.revoked_at, rp.revoked_by
    FROM role_permission rp
    WHERE rp.role_code IN ('ADMIN_DIOCESE', 'ADMIN_DEANERY', 'ADMIN_PARISH')
      AND rp.revoked_at IS NOT NULL
      AND rp.revocation_reason = 'MANUAL';

    INSERT INTO role_permission_unit_override (id, role_code, permission_code, scope_level, diocese_id, override_type,
                                               assigned_by, assigned_at, created_at, created_by, updated_at, updated_by, status)
    SELECT gen_random_uuid()::text, v.role_code, v.permission_code, 'DIOCESE', v_diocese_id, 'EXCLUDE',
           v.revoked_by, v.revoked_at, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', TRUE
    FROM v141_revoked v
    ON CONFLICT (role_code, permission_code, unit_id, override_type) DO NOTHING;

    UPDATE role_permission rp
    SET revoked_at        = NULL,
        revoked_by        = NULL,
        revocation_reason = NULL,
        updated_at        = CURRENT_TIMESTAMP,
        updated_by        = 'SYSTEM'
    FROM v141_revoked v
    WHERE rp.id = v.id;

    GET DIAGNOSTICS v_converted = ROW_COUNT;
    RAISE NOTICE 'V141: đã chuyển % thu hồi toàn hệ thống thành thu hồi riêng cho giáo phận Phú Cường.', v_converted;
END
$$;
