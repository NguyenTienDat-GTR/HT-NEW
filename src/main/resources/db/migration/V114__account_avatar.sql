-- Ảnh đại diện của tài khoản không gắn hồ sơ huynh trưởng (tài khoản gắn huynh trưởng dùng ảnh trong hồ sơ huynh trưởng).
ALTER TABLE account ADD COLUMN IF NOT EXISTS image_asset_id UUID REFERENCES media_asset (id);
CREATE INDEX IF NOT EXISTS idx_account_image_asset ON account (image_asset_id) WHERE image_asset_id IS NOT NULL;
