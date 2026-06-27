package phucuong.ht.modules.leader.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;

@Getter
@Setter
public class LeaderCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String holyName;

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotNull
    private LocalDate birthDate;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 15)
    private String phoneNumber;

    @NotNull
    private LeaderLevel leaderLevel;

    @NotBlank
    private String parishCode;
}
