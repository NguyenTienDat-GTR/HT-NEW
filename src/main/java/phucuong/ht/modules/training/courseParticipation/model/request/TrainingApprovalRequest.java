package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrainingApprovalRequest {
    @NotNull
    private Boolean approved;
    private String review;

    // Hồ sơ ngoại lệ: người duyệt chọn cấp độ đạt được sau khi hoàn thành khóa (bắt buộc khi duyệt hồ sơ ngoại lệ).
    // Phải cao hơn cấp hiện tại của huynh trưởng và không cao hơn cấp độ đạt được của loại khóa.
    private String achievedLevel;
}
