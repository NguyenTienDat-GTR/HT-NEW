package phucuong.ht.common.excel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lọc cột theo lựa chọn của người dùng (theo tên cột), giữ đúng thứ tự gốc. */
public final class ExcelColumns {

    private ExcelColumns() {
    }

    /** selected rỗng hoặc không khớp cột nào thì giữ tất cả cột. */
    public static <T> List<ExcelColumn<T>> select(List<ExcelColumn<T>> all, List<String> selected) {
        if (selected == null || selected.isEmpty()) {
            return all;
        }
        Set<String> wanted = new HashSet<>(selected);
        List<ExcelColumn<T>> picked = all.stream().filter(column -> wanted.contains(column.header())).toList();
        return picked.isEmpty() ? all : picked;
    }

    public static <T> List<String> headers(List<ExcelColumn<T>> columns) {
        return columns.stream().map(ExcelColumn::header).toList();
    }
}
