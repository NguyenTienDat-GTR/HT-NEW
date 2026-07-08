package phucuong.ht.modules.leader.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaderCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String holyName;

    @NotBlank
    @Size(max = 200)
    private String fullName;

    @JsonIgnore
    private String firstName;

    @JsonIgnore
    private String lastName;

    @NotNull
    private LocalDate birthDate;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải là số điện thoại Việt Nam gồm 10 chữ số và bắt đầu bằng 0")
    @Size(max = 15)
    private String phoneNumber;

    @NotNull
    private LeaderGender gender;

    @NotNull
    private LeaderLevel leaderLevel;

    @NotNull
    private UUID parishId;
}
