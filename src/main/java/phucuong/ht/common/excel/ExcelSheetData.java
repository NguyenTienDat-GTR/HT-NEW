package phucuong.ht.common.excel;

import java.util.List;
import java.util.Map;

/** Dữ liệu một sheet đã đọc: tên sheet, các cột có trong tiêu đề và danh sách dòng dữ liệu (đã bỏ dòng trống). */
public record ExcelSheetData(String name, Map<String, String> columns, List<ExcelRowData> rows) {

    public boolean hasColumn(String column) {
        return columns.containsKey(ExcelWorkbookReader.normalize(column));
    }

    /** Ghi lỗi nếu thiếu cột bắt buộc trong dòng tiêu đề; trả true khi đủ cột. */
    public boolean requireColumns(ImportErrors errors, String... required) {
        boolean ok = true;
        for (String column : required) {
            if (!hasColumn(column)) {
                errors.add(name, 1, column, "Thiếu cột \"" + column + "\" trong dòng tiêu đề. Hãy dùng đúng file mẫu");
                ok = false;
            }
        }
        return ok;
    }
}
