package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class RegistrationCandidateResponse {
    private UUID id;
    private String holyName;
    private String fullName;
    private LocalDate birthDate;
    private String phoneNumber;
    private String email;
    private String leaderLevel;
    // Năm đạt cấp độ hiện tại (theo năm duyệt chứng nhận, hoặc năm tổ chức khóa nếu khóa không cấp chứng nhận); null nếu chưa có lịch sử cấp bậc.
    private Integer levelAchievedYear;
    private UUID parishId;
    private String parishName;
    private UUID deaneryId;
    private String deaneryName;
    // true = đủ điều kiện theo loại khóa và số năm hoạt động của khóa.
    private Boolean eligible;
    // true = giáo hạt của huynh trưởng nằm ngoài các giáo hạt mà khóa dành cho: gửi phải kèm lí do riêng và duyệt thủ công.
    private Boolean offTarget;
}
