package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TrainingBulkApprovalRequest {
    @NotEmpty
    private List<String> ids;

    @NotNull
    private Boolean approved;

    private String review;

    // Lý do riêng theo từng hồ sơ (id -> lý do); ưu tiên hơn lý do chung. Bắt buộc có lý do khi từ chối.
    private java.util.Map<String, String> reviews;

    // Cấp độ đạt được của hồ sơ ngoại lệ khi duyệt: chung cho mọi hồ sơ ngoại lệ, hoặc riêng theo id (ưu tiên hơn).
    private String achievedLevel;
    private java.util.Map<String, String> achievedLevels;
}
