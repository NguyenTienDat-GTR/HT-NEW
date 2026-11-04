-- Lịch sử phiên bản cấu hình chứng nhận / thẻ: mỗi lần lưu ghi lại bản trước khi đổi để người dùng xem và khôi phục.
CREATE TABLE layout_config_version (
    id          UUID PRIMARY KEY,
    config_type VARCHAR(20)  NOT NULL,
    config_id   UUID         NOT NULL,
    version_no  INTEGER      NOT NULL,
    snapshot    JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    CONSTRAINT uk_layout_config_version UNIQUE (config_type, config_id, version_no),
    CONSTRAINT ck_layout_config_version_type CHECK (config_type IN ('CERTIFICATE', 'BADGE'))
);

CREATE INDEX idx_layout_config_version_lookup ON layout_config_version (config_type, config_id, version_no DESC);
