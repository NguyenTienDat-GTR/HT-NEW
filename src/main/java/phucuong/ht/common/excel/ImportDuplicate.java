package phucuong.ht.common.excel;

import java.util.List;
import java.util.UUID;

/**
 * Một dòng nghi trùng với hồ sơ huynh trưởng đã có, được giữ lại chờ người dùng xác nhận (các dòng còn lại vẫn được import).
 * Họ tên, ngày sinh, giáo xứ của hồ sơ đã có giống hệt dòng này nên chỉ trả số điện thoại (che bớt) để người dùng phân biệt.
 */
public record ImportDuplicate(String sheet, int row, String holyName, String fullName, String birthDate, String parish, String phone,
                              List<ExistingLeader> existing) {

    public record ExistingLeader(UUID id, String maskedPhone) {
    }

    public ImportDuplicate {
        existing = existing == null ? List.of() : List.copyOf(existing);
    }
}
