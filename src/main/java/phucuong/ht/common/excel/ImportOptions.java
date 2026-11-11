package phucuong.ht.common.excel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tùy chọn của một lần import. Có quyết định nghĩa là lần chạy tiếp theo để xử lý các dòng nghi trùng đã được người dùng xác nhận:
 * chỉ các dòng có quyết định được xử lý, các dòng khác của file bỏ qua hoàn toàn (đã import ở lần trước).
 */
public final class ImportOptions {

    public static final ImportOptions NONE = new ImportOptions(List.of());

    private final Map<String, DuplicateDecision> byKey = new HashMap<>();

    public ImportOptions(List<DuplicateDecision> decisions) {
        if (decisions != null) {
            for (DuplicateDecision decision : decisions) {
                byKey.put(decision.key(), decision);
            }
        }
    }

    public boolean isResolution() {
        return !byKey.isEmpty();
    }

    public DuplicateDecision decisionFor(ExcelRowData row) {
        return byKey.get(DuplicateDecision.key(row.sheet(), row.number()));
    }

    /** Lần chạy xử lý quyết định chỉ giữ các dòng đã có quyết định; lần import thường giữ nguyên mọi dòng. */
    public List<ExcelRowData> filter(List<ExcelRowData> rows) {
        if (!isResolution()) {
            return rows;
        }
        return rows.stream().filter(row -> decisionFor(row) != null).toList();
    }
}
