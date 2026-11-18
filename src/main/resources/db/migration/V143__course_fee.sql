-- ============================================================================
-- V143: Lệ phí khóa huấn luyện.
--       - training_course: cờ thu lệ phí và lệ phí mỗi người (đồng, số nguyên). Bật thì phải có số tiền > 0, tắt thì không giữ số tiền.
--       - course_participation: thời điểm và người ghi nhận đã đóng lệ phí; null = chưa đóng.
-- ============================================================================

ALTER TABLE training_course
    ADD COLUMN IF NOT EXISTS fee_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fee_amount BIGINT;

ALTER TABLE training_course DROP CONSTRAINT IF EXISTS chk_training_course_fee;
ALTER TABLE training_course
    ADD CONSTRAINT chk_training_course_fee CHECK (
        (fee_enabled = TRUE AND fee_amount IS NOT NULL AND fee_amount > 0 AND fee_amount <= 999999999)
        OR (fee_enabled = FALSE AND fee_amount IS NULL)
    );

ALTER TABLE course_participation
    ADD COLUMN IF NOT EXISTS fee_paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS fee_paid_by VARCHAR(100);

-- Chỉ các hồ sơ đã đóng mới vào chỉ mục: dùng để biết khóa đã có người đóng lệ phí chưa.
CREATE INDEX IF NOT EXISTS idx_course_participation_fee_paid ON course_participation (course_id) WHERE fee_paid_at IS NOT NULL;
