package phucuong.ht.modules.executiveBoard.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Một vị trí trong ban điều hành của đơn vị: chức vụ và huynh trưởng đang giữ (assigned = false khi chưa phân công).
 */
@Getter
@Builder
public class ExecutiveBoardSeatResponse {
    private String positionCode;
    private String positionName;
    private boolean assigned;
    private UUID assignmentId;
    private UUID leaderId;
    private String leaderHolyName;
    private String leaderFullName;
    private String leaderEmail;
    private String leaderPhoneNumber;
    private String leaderImageUrl;
    private String leaderParishName;
    private String leaderDeaneryName;
    private LocalDate startDate;
    private LocalDate endDate;
}
