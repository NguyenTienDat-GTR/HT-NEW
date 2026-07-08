package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TrainingRegistrationRequest {
    @NotNull
    private UUID leaderId;
    private String exceptionReason;
}
