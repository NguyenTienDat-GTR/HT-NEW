package phucuong.ht.common.excel;

import java.util.List;

/** Kết quả import thành công, kèm kế hoạch gắn ảnh chạy sau khi transaction commit và các dòng nghi trùng đang chờ người dùng xác nhận. */
public record ImportSummary(int created, String message, List<AvatarPlan> avatarPlan, List<AvatarWarning> avatarWarnings,
                            List<ImportDuplicate> duplicates) {

    public ImportSummary(int created, String message) {
        this(created, message, List.of(), List.of(), List.of());
    }

    public ImportSummary(int created, String message, List<AvatarPlan> avatarPlan, List<AvatarWarning> avatarWarnings) {
        this(created, message, avatarPlan, avatarWarnings, List.of());
    }

    public ImportSummary {
        avatarPlan = avatarPlan == null ? List.of() : List.copyOf(avatarPlan);
        avatarWarnings = avatarWarnings == null ? List.of() : List.copyOf(avatarWarnings);
        duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
    }
}
