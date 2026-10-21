package phucuong.ht.modules.training.courseParticipation.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class RegistrationCandidateResponse {
    private UUID id;
    private String holyName;
    private String fullName;
    private LocalDate birthDate;
    private String phoneNumber;
    private String email;
    private String leaderLevel;
    private UUID parishId;
    private String parishName;
    private UUID deaneryId;
    private String deaneryName;
    // true = đủ điều kiện theo loại khóa và số năm hoạt động của khóa.
    private Boolean eligible;
}
