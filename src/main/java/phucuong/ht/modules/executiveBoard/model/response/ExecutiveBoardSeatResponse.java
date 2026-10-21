package phucuong.ht.modules.executiveBoard.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Một vị trí trong ban điều hành của đơn vị: chức vụ và huynh trưởng đang giữ (assigned = false khi chưa phân công).
 * Linh mục tuyên úy (chaplain = true) luôn đứng đầu với thứ tự 0; tên lấy từ thông tin đơn vị.
 */
@Getter
@Builder
public class ExecutiveBoardSeatResponse {
    private String positionCode;
    private String positionName;
    private Integer displayOrder;
    private boolean chaplain;
    private boolean assigned;
    // Chức vụ còn người giữ (đang bật, chưa hết hạn), kể cả nhiệm kì đặt trước chưa tới ngày: không phân công thêm được.
    private boolean occupied;
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
