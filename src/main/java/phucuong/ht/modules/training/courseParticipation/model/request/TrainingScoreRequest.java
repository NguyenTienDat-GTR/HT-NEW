package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class TrainingScoreRequest {
    @Valid
    @NotEmpty
    private List<ComponentScoreRequest> componentScores;

    private String review;
}
