-- Lưu ảnh trên cloud: bảng media_asset (siêu dữ liệu ảnh, không chứa URL của nhà cung cấp) + cột tham chiếu.
-- Các cột base64 cũ (leader.image_url, certificate_config.background_image, badge_config.*_background)
-- được GIỮ LẠI để job chuyển dữ liệu cũ đọc; sẽ drop ở bản phát hành sau khi không còn dòng nào chứa data URL.

ALTER TABLE diocese ADD COLUMN IF NOT EXISTS storage_folder VARCHAR(150);

-- Thư mục giáo phận trên cloud = tên không dấu (slug) + 8 ký tự đầu của id. Ổn định, không đổi khi đổi tên giáo phận.
UPDATE diocese
SET storage_folder = left(
        trim(both '-' from regexp_replace(lower(translate(name, 'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ', 'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydaaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd')), '[^a-z0-9]+', '-', 'g')),
        100) || '-' || substr(replace(id::text, '-', ''), 1, 8)
WHERE storage_folder IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_diocese_storage_folder ON diocese (storage_folder) WHERE storage_folder IS NOT NULL;

CREATE TABLE IF NOT EXISTS media_asset (
    id                UUID PRIMARY KEY,
    category          VARCHAR(40)  NOT NULL,
    diocese_id        UUID         NULL REFERENCES diocese (id),
    status            VARCHAR(16)  NOT NULL,
    provider          VARCHAR(40)  NOT NULL,
    base_key          VARCHAR(400) NOT NULL,
    variants          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_by        VARCHAR(100),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    deleted_at        TIMESTAMPTZ  NULL,
    migration_error   VARCHAR(500) NULL,
    migration_retries INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS idx_media_asset_status_created ON media_asset (status, created_at);
CREATE INDEX IF NOT EXISTS idx_media_asset_provider ON media_asset (provider) WHERE status = 'ACTIVE';

ALTER TABLE leader ADD COLUMN IF NOT EXISTS image_asset_id UUID NULL REFERENCES media_asset (id);
ALTER TABLE certificate_config ADD COLUMN IF NOT EXISTS background_asset_id UUID NULL REFERENCES media_asset (id);
ALTER TABLE badge_config ADD COLUMN IF NOT EXISTS front_background_asset_id UUID NULL REFERENCES media_asset (id);
ALTER TABLE badge_config ADD COLUMN IF NOT EXISTS back_background_asset_id UUID NULL REFERENCES media_asset (id);

-- Job chuyển dữ liệu cũ tìm nhanh các dòng còn data URL.
CREATE INDEX IF NOT EXISTS idx_leader_legacy_image ON leader (id) WHERE image_asset_id IS NULL AND image_url LIKE 'data:%';
