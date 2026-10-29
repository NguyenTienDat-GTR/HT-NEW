-- Album ảnh nền của chứng nhận và thẻ: mỗi phạm vi (SYSTEM | DIOCESE:{id} | DEANERY:{id}) giữ tối đa N ảnh cho mỗi loại.
-- Mỗi phạm vi chỉ thấy album của chính mình; cấu hình dùng một BẢN SAO ảnh trong album nên xóa ảnh album không ảnh hưởng cấu hình.
CREATE TABLE IF NOT EXISTS background_album_item
(
    id         UUID PRIMARY KEY,
    scope_key  VARCHAR(80)  NOT NULL,
    category   VARCHAR(40)  NOT NULL,
    asset_id   UUID         NOT NULL UNIQUE REFERENCES media_asset (id),
    created_by VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_background_album_scope ON background_album_item (scope_key, category, created_at);
