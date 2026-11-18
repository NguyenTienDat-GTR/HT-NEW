package phucuong.ht.common.excel;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Thứ tự dòng mặc định của file Excel khi người dùng không chọn cách sắp xếp: giáo hạt, giáo xứ rồi tên (A-Z theo bảng chữ cái tiếng Việt).
 * Giá trị trống xếp cuối.
 */
public final class ExcelRowOrder {

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private ExcelRowOrder() {
    }

    /** So sánh lần lượt theo từng khóa, mỗi khóa theo A-Z tiếng Việt, khóa trống xếp sau cùng. */
    @SafeVarargs
    public static <R> Comparator<R> byText(Function<R, String>... keys) {
        Collator collator = Collator.getInstance(VIETNAMESE);
        collator.setStrength(Collator.TERTIARY);
        Comparator<String> text = Comparator.nullsLast(collator::compare);
        Comparator<R> order = null;
        for (Function<R, String> key : keys) {
            Comparator<R> next = Comparator.comparing(row -> blankToNull(key.apply(row)), text);
            order = order == null ? next : order.thenComparing(next);
        }
        return order == null ? (left, right) -> 0 : order;
    }

    /** Tên (từ cuối của họ tên) — không phải tên thánh hay họ. Họ tên "Nguyễn Văn An" → "An". */
    public static String givenName(String fullName) {
        if (fullName == null) {
            return null;
        }
        String trimmed = fullName.trim();
        int space = trimmed.lastIndexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(space + 1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
