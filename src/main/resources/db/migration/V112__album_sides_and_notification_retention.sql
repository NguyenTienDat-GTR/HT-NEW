-- Album ảnh nền của thẻ tách theo mặt: mỗi mặt (trước / sau) có album riêng, tối đa N ảnh mỗi mặt.
ALTER TABLE background_album_item ADD COLUMN IF NOT EXISTS album_kind VARCHAR(20);

UPDATE background_album_item
SET album_kind = CASE category WHEN 'CERTIFICATE_BACKGROUND' THEN 'CERTIFICATE' ELSE 'BADGE_FRONT' END
WHERE album_kind IS NULL;

ALTER TABLE background_album_item ALTER COLUMN album_kind SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_background_album_kind ON background_album_item (scope_key, album_kind, created_at);

-- Thông báo đã đọc tự bị xóa sau 15 ngày kể từ lúc đọc: chỉ mục để job dọn quét nhanh.
CREATE INDEX IF NOT EXISTS idx_training_notification_read_at_only
    ON training_notification (read_at) WHERE read_at IS NOT NULL;
