package phucuong.ht.common.excel;

import java.util.ArrayList;
import java.util.List;

/** Bộ gom lỗi khi kiểm tra file import; chỉ giữ tối đa {@value #LIMIT} lỗi đầu tiên. */
public class ImportErrors {

    public static final int LIMIT = 200;

    private final List<ImportError> errors = new ArrayList<>();
    private int total;

    public void add(String sheet, int row, String column, String message) {
        total++;
        if (errors.size() < LIMIT) {
            errors.add(new ImportError(sheet, row, column, message));
        }
    }

    /** Tổng số lỗi đã ghi nhận (kể cả lỗi vượt giới hạn hiển thị). */
    public int count() {
        return total;
    }

    public boolean isEmpty() {
        return total == 0;
    }

    public void throwIfAny() {
        if (total > 0) {
            if (total > errors.size()) {
                errors.add(new ImportError("", 0, "", "Còn " + (total - errors.size()) + " lỗi khác chưa hiển thị"));
            }
            throw new ImportValidationException(errors);
        }
    }
}
