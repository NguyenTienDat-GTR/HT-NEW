package phucuong.ht.modules.leader.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaderUpdateRequest {

    @Size(max = 50)
    private String holyName;

    @Size(max = 200)
    private String fullName;

    @JsonIgnore
    private String firstName;

    @JsonIgnore
    private String lastName;

    private LocalDate birthDate;

    @Email
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải là số điện thoại Việt Nam gồm 10 chữ số và bắt đầu bằng 0")
    @Size(max = 15)
    private String phoneNumber;

    @Size(max = 500)
    private String imageUrl;

    private LeaderGender gender;

    private UUID parishId;
}
