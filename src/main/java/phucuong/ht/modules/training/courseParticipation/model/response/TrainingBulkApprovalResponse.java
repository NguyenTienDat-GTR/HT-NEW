package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TrainingBulkApprovalResponse {
    private Integer successCount;
    private Integer failedCount;
    private List<Failure> failures;

    @Getter
    @Builder
    public static class Failure {
        private String id;
        private String leaderFullName;
        private String reason;
    }
}
