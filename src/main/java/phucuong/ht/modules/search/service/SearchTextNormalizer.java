package phucuong.ht.modules.search.service;

import java.text.Normalizer;
import java.util.Locale;

/** Chuẩn hóa từ khóa giống hàm SQL immutable_unaccent: bỏ dấu, đ thành d, chữ thường. */
final class SearchTextNormalizer {
    private SearchTextNormalizer() {
    }

    static String normalize(String value) {
        if (value == null) return "";
        String replaced = value.replace('đ', 'd').replace('Đ', 'd');
        String stripped = Normalizer.normalize(replaced, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return stripped.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Thoát ký tự đặc biệt của LIKE để từ khóa luôn được hiểu là chữ thường. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
