-- Bằng chứng đồng ý xử lý dữ liệu cá nhân (Luật 91/2025/QH15 Điều 9, NĐ 356/2025 Điều 6):
-- ai đồng ý, lúc nào, với phiên bản chính sách nào, qua chức năng nào.
--   SELF              : người dùng tự đồng ý cho dữ liệu của chính mình.
--   ADMIN_ATTESTATION : quản trị viên xác nhận người có dữ liệu đã được thông báo và đồng ý khi nhập hộ.
CREATE TABLE privacy_consent (
    id                UUID PRIMARY KEY,
    consent_type      VARCHAR(20)  NOT NULL,
    username          VARCHAR(100),
    subject_leader_id UUID,
    recorded_by       VARCHAR(100) NOT NULL,
    purposes          VARCHAR(500) NOT NULL,
    policy_version    VARCHAR(20)  NOT NULL,
    source            VARCHAR(40)  NOT NULL,
    ip_address        VARCHAR(64),
    user_agent        VARCHAR(500),
    consented_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    withdrawn_at      TIMESTAMPTZ,
    CONSTRAINT ck_privacy_consent_type CHECK (consent_type IN ('SELF', 'ADMIN_ATTESTATION'))
);

CREATE INDEX idx_privacy_consent_username ON privacy_consent (username, consented_at DESC);
CREATE INDEX idx_privacy_consent_subject_leader ON privacy_consent (subject_leader_id);
