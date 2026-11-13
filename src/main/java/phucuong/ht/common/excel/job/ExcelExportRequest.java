package phucuong.ht.common.excel.job;

import phucuong.ht.common.repository.BaseSearchRequest;

import java.util.List;
import java.util.Map;

/**
 * Yêu cầu xuất của một tác vụ nền: bộ lọc/tìm kiếm/sắp xếp của danh sách, các cột người dùng chọn (rỗng = tất cả cột)
 * và tùy chọn riêng của từng loại xuất (vd. xuất theo mẫu đăng ký: cách gom nhóm, cách chia sheet/file).
 */
public record ExcelExportRequest(BaseSearchRequest search, List<String> columns, Map<String, String> options) {

    public ExcelExportRequest {
        search = search == null ? new BaseSearchRequest() : search;
        columns = columns == null ? List.of() : List.copyOf(columns);
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    public static ExcelExportRequest of(BaseSearchRequest search) {
        return new ExcelExportRequest(search, List.of(), Map.of());
    }

    public String option(String name) {
        String value = options.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
