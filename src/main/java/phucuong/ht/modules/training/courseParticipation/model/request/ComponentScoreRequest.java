package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ComponentScoreRequest {

    @NotBlank
    private String componentId;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal score;

    private String note;
}
