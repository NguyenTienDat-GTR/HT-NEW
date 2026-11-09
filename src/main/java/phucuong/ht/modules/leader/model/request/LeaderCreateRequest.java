package phucuong.ht.modules.leader.model.request;

import phucuong.ht.modules.privacy.model.SubjectConsentRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class LeaderCreateRequest extends SubjectConsentRequest {

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

    // Email không bắt buộc.
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải là số điện thoại Việt Nam gồm 10 chữ số và bắt đầu bằng 0")
    @Size(max = 15)
    private String phoneNumber;

    // Id ảnh đại diện đã tải lên qua POST /api/media (category LEADER_AVATAR).
    private UUID imageAssetId;

    @NotNull
    private LeaderGender gender;

    @NotNull
    private String leaderLevel;

    @NotNull
    private UUID parishId;
}
