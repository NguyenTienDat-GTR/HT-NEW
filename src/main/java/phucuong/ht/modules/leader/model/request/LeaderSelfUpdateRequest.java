package phucuong.ht.modules.leader.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.leader.model.enums.LeaderGender;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin cá nhân huynh trưởng tự sửa trong hồ sơ của mình. Không gồm giáo xứ / giáo hạt / cấp độ.
 * Trường null = giữ nguyên. Tên thuộc tính trùng tên thuộc tính của Leader để BaseRepo sao chép tự động.
 */
@Getter
@Setter
public class LeaderSelfUpdateRequest {

    private String holyName;
    private String fullName;
    private LocalDate birthDate;
    private LeaderGender gender;
    private String email;
    private String phoneNumber;

    /** Id ảnh đại diện mới đã tải lên qua POST /api/media; null = giữ nguyên ảnh. */
    private UUID imageAssetId;

    /** true = gỡ ảnh đại diện hiện tại. */
    private Boolean clearImage;

    // Do service tách từ fullName.
    @JsonIgnore
    private String firstName;
    @JsonIgnore
    private String lastName;
}
