package phucuong.ht.common.excel;

import java.util.UUID;

/**
 * Quyết định của người dùng cho một dòng nghi trùng hồ sơ huynh trưởng (khớp tên thánh, họ tên, ngày sinh, giáo xứ nhưng khác số điện thoại).
 * {@code sameLeaderId} có giá trị: cùng một người với huynh trưởng đã có đó; null: hai người khác nhau.
 */
public record DuplicateDecision(String sheet, int row, UUID sameLeaderId) {

    public String key() {
        return key(sheet, row);
    }

    public static String key(String sheet, int row) {
        return (sheet == null ? "" : sheet) + "#" + row;
    }
}
