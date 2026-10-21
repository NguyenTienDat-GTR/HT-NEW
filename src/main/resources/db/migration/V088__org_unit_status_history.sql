-- Lịch sử bật/tắt đơn vị (giáo phận, giáo hạt, giáo xứ) để truy vết và phân tích theo thời điểm.
CREATE TABLE IF NOT EXISTS org_unit_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_type VARCHAR(20) NOT NULL,
    unit_id UUID NOT NULL,
    status BOOLEAN NOT NULL,
    event_type VARCHAR(20) NOT NULL DEFAULT 'TOGGLE',
    reason VARCHAR(500),
    changed_by VARCHAR(255),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    affected_account_count INTEGER NOT NULL DEFAULT 0,
    affected_child_unit_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_org_unit_status_history_unit
    ON org_unit_status_history (unit_type, unit_id, changed_at DESC);

-- Dòng nền cho trạng thái hiện tại của mọi đơn vị.
INSERT INTO org_unit_status_history (unit_type, unit_id, status, event_type, reason, changed_by, changed_at)
SELECT 'DIOCESE', id, COALESCE(status, TRUE), 'BASELINE', NULL, 'SYSTEM', CURRENT_TIMESTAMP FROM diocese
UNION ALL
SELECT 'DEANERY', id, COALESCE(status, TRUE), 'BASELINE', NULL, 'SYSTEM', CURRENT_TIMESTAMP FROM deanery
UNION ALL
SELECT 'PARISH', id, COALESCE(status, TRUE), 'BASELINE', NULL, 'SYSTEM', CURRENT_TIMESTAMP FROM parish;
