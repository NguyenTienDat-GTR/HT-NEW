package phucuong.ht.modules.training.courseParticipation.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TrainingSubmissionRecipientRequest {
    private List<String> recipientUsernames;
}
