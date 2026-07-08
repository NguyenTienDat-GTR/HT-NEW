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
}
