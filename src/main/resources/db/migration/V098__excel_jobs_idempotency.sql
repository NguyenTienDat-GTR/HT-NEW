-- 1) Tác vụ nền xuất / nhập Excel. Mỗi (người tạo, loại, Idempotency-Key) chỉ có một job: gửi lại cùng key trả lại đúng job cũ.
CREATE TABLE IF NOT EXISTS excel_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner           VARCHAR(100) NOT NULL,
    kind            VARCHAR(40)  NOT NULL,
    -- Đối tượng đích của job (ví dụ mã khóa khi import danh sách tham dự); rỗng nếu không có.
    target          VARCHAR(255) NOT NULL DEFAULT '',
    idempotency_key VARCHAR(100) NOT NULL,
    payload_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    processed       INTEGER      NOT NULL DEFAULT 0,
    total           INTEGER      NOT NULL DEFAULT 0,
    params_json     TEXT,
    -- Ảnh chụp người tạo (username, leaderId, quyền) để job chạy nền với đúng phạm vi quyền.
    principal_json  TEXT         NOT NULL,
    input_path      VARCHAR(500),
    output_path     VARCHAR(500),
    output_name     VARCHAR(255),
    result_json     TEXT,
    error_message   TEXT,
    warning         TEXT,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    cancel_requested BOOLEAN     NOT NULL DEFAULT FALSE,
    lease_until     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    CONSTRAINT uk_excel_job_idempotency UNIQUE (owner, kind, idempotency_key),
    CONSTRAINT chk_excel_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

-- Mỗi người chỉ có một job đang xếp hàng / đang chạy cho cùng loại và cùng đối tượng đích.
CREATE UNIQUE INDEX IF NOT EXISTS uk_excel_job_active
    ON excel_job (owner, kind, target)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX IF NOT EXISTS idx_excel_job_status_created ON excel_job (status, created_at);
CREATE INDEX IF NOT EXISTS idx_excel_job_owner_created ON excel_job (owner, created_at DESC);

-- 2) Idempotency cho thao tác duyệt / từ chối / thu hồi / cấp lại chứng nhận: bản ghi được ghi CÙNG transaction với thao tác,
-- nên thao tác thất bại (rollback) thì key không bị giữ; gửi lại cùng key sau khi thành công nhận lại đúng kết quả cũ.
CREATE TABLE IF NOT EXISTS idempotency_record (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner           VARCHAR(100) NOT NULL,
    scope           VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    payload_hash    VARCHAR(64)  NOT NULL,
    response_json   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_idempotency_record UNIQUE (owner, scope, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_idempotency_record_created ON idempotency_record (created_at);

-- 3) Email huynh trưởng duy nhất không phân biệt hoa thường (khớp với cách import / tìm huynh trưởng theo email).
CREATE UNIQUE INDEX IF NOT EXISTS uk_leader_email_lower ON leader (lower(email));
