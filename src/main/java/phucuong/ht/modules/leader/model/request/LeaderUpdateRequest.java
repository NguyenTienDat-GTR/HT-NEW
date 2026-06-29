package phucuong.ht.modules.leader.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaderUpdateRequest {

    @Size(max = 50)
    private String holyName;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    private LocalDate birthDate;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 15)
    private String phoneNumber;

    private LeaderLevel leaderLevel;

    private UUID parishId;
}
