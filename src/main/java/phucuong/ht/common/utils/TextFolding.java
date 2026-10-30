package phucuong.ht.common.utils;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Xử lý chuỗi/bộ lọc dùng chung cho các màn cấu hình (chứng nhận, thẻ tham dự): chuẩn hóa, bỏ dấu, tên không trùng. */
public final class TextFolding {

    private TextFolding() {
    }

    public static String filterText(Map<String, Object> filters, String key) {
        Object value = filters.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    public static Boolean filterBoolean(Map<String, Object> filters, String key) {
        String value = filterText(filters, key);
        return value == null ? null : Boolean.valueOf(value);
    }

    /** Chữ thường, bỏ dấu tiếng Việt để tìm kiếm thân thiện. */
    public static String foldText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase().trim();
    }

    /** Cắt khoảng trắng hai đầu; chuỗi rỗng coi như không có (null). */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Chọn tên chưa dùng trong phạm vi: thêm hậu tố " (n)" khi trùng (không phân biệt hoa thường).
     * {@code currentName} là tên hiện tại của chính cấu hình đang sửa nên không tính là trùng.
     */
    public static String uniqueName(Collection<String> existingNames, String requested, String currentName, String defaultName, int maxLength) {
        String base = normalize(requested);
        if (base == null) {
            base = defaultName;
        }
        if (base.length() > maxLength) {
            base = base.substring(0, maxLength).trim();
        }
        Set<String> taken = new HashSet<>();
        for (String name : existingNames) {
            taken.add(name.toLowerCase());
        }
        if (currentName != null) {
            taken.remove(currentName.toLowerCase());
        }
        String candidate = base;
        for (int n = 1; taken.contains(candidate.toLowerCase()); n++) {
            candidate = base + " (" + n + ")";
        }
        return candidate;
    }
}
