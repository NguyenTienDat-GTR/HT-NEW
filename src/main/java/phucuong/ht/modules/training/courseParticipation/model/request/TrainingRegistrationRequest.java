package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class TrainingRegistrationRequest {

    @NotEmpty
    private List<UUID> leaderIds;

    private List<String> recipientUsernames;

    private Map<UUID, String> exceptionReasonsByLeaderId;
}
