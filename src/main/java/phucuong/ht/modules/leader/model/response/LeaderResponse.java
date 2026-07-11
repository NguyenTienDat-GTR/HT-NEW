package phucuong.ht.modules.leader.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class LeaderResponse {

    private UUID id;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private Boolean status;
    private String holyName;
    private String fullName;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String email;
    private String phoneNumber;
    private String imageUrl;
    private LeaderGender gender;
    private LeaderLevel leaderLevel;
    private UUID parishId;
}
