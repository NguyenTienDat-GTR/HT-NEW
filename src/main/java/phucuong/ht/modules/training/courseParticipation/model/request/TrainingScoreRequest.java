package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TrainingScoreRequest {
    @NotNull
    private BigDecimal totalScore;
    private String review;
}
