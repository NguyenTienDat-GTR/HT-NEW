package phucuong.ht.common.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Một dòng dữ liệu của file import; các hàm đọc ghi lỗi (kèm sheet, dòng, cột) vào {@link ImportErrors}. */
public final class ExcelRowData {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String sheet;
    private final int number;
    private final Map<String, Object> values;

    ExcelRowData(String sheet, int number, Map<String, Object> values) {
        this.sheet = sheet;
        this.number = number;
        this.values = values;
    }

    public String sheet() {
        return sheet;
    }

    /** Số dòng theo Excel (dòng tiêu đề là 1). */
    public int number() {
        return number;
    }

    /** Văn bản đã cắt khoảng trắng; null nếu trống hoặc không có cột. */
    public String text(String column) {
        Object value = values.get(ExcelWorkbookReader.normalize(column));
        if (value == null) {
            return null;
        }
        String text = asText(value).trim();
        return text.isEmpty() ? null : text;
    }

    public String requiredText(ImportErrors errors, String column) {
        String text = text(column);
        if (text == null) {
            error(errors, column, "Bắt buộc nhập");
        }
        return text;
    }

    /** Số điện thoại: Excel hay làm mất số 0 đầu khi ô là số, nên 9 chữ số được thêm lại số 0. */
    public String phone(String column) {
        String text = text(column);
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[\\s.\\-()]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        }
        if (digits.matches("\\d{9}")) {
            digits = "0" + digits;
        }
        return digits;
    }

    public LocalDate date(ImportErrors errors, String column, boolean required) {
        Object value = values.get(ExcelWorkbookReader.normalize(column));
        if (value == null || (value instanceof String s && s.isBlank())) {
            if (required) {
                error(errors, column, "Bắt buộc nhập");
            }
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        LocalDate parsed = value instanceof String s ? ExcelWorkbookReader.parseDate(s) : null;
        if (parsed == null) {
            error(errors, column, "Ngày không hợp lệ, cần theo dạng dd/MM/yyyy");
        }
        return parsed;
    }

    public BigDecimal decimal(ImportErrors errors, String column, boolean required) {
        Object value = values.get(ExcelWorkbookReader.normalize(column));
        if (value == null || (value instanceof String s && s.isBlank())) {
            if (required) {
                error(errors, column, "Bắt buộc nhập");
            }
            return null;
        }
        try {
            return value instanceof BigDecimal number ? number : new BigDecimal(asText(value).trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            error(errors, column, "Phải là số");
            return null;
        }
    }

    public void error(ImportErrors errors, String column, String message) {
        errors.add(sheet, number, column, message);
    }

    static String asText(Object value) {
        if (value instanceof BigDecimal number) {
            BigDecimal stripped = number.stripTrailingZeros();
            return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
        }
        if (value instanceof LocalDate date) {
            return date.format(DISPLAY_DATE);
        }
        return value.toString();
    }
}
