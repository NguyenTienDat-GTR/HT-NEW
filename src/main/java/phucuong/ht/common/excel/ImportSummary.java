package phucuong.ht.common.excel;

import java.util.List;

/** Kết quả import thành công, kèm kế hoạch gắn ảnh chạy sau khi transaction commit. */
public record ImportSummary(int created, String message, List<AvatarPlan> avatarPlan, List<AvatarWarning> avatarWarnings) {

    public ImportSummary(int created, String message) {
        this(created, message, List.of(), List.of());
    }

    public ImportSummary {
        avatarPlan = avatarPlan == null ? List.of() : List.copyOf(avatarPlan);
        avatarWarnings = avatarWarnings == null ? List.of() : List.copyOf(avatarWarnings);
    }
}
