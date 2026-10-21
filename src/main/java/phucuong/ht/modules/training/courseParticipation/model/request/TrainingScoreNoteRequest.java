package phucuong.ht.modules.training.courseParticipation.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrainingScoreNoteRequest {

    // Rỗng hoặc null = xóa ghi chú.
    @Size(max = 1000)
    private String note;
}
