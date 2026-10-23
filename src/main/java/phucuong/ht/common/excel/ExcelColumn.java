package phucuong.ht.common.excel;

import java.util.function.Function;

/** Một cột của file Excel xuất ra: tiêu đề, cách lấy giá trị từ một dòng dữ liệu và độ rộng (ký tự). */
public record ExcelColumn<T>(String header, Function<T, Object> value, int width) {

    public static <T> ExcelColumn<T> of(String header, Function<T, Object> value) {
        return new ExcelColumn<>(header, value, 20);
    }

    public static <T> ExcelColumn<T> of(String header, int width, Function<T, Object> value) {
        return new ExcelColumn<>(header, value, width);
    }
}
